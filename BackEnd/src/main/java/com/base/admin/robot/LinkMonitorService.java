package com.base.admin.robot;

import com.base.admin.exception.BusinessException;
import com.base.admin.service.DingTalkCalendarClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkMonitorService {

    private static final Pattern URL_PAT = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    /** 浏览器 UA：部分站点对爬虫 UA 走登录页/首页，会掩盖真实失效跳转。 */
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String DEFAULT_COOKIE_HOSTS = "xiaohongshu.com,xhslink.com";
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Cookie 健康检查结果缓存（避免批量探测时对站点连打）。 */
    private static final long COOKIE_HEALTH_CACHE_MS = 10 * 60 * 1000L;
    private final JdbcTemplate jdbc;
    private final DingTalkCalendarClient dingTalkCalendarClient;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicReference<CookieHealthCache> cookieHealthCache = new AtomicReference<>();

    private record CookieHealthCache(String cookieFp, boolean alive, String reason, long checkedAtMs) {
    }

    private record CookiePrecheck(boolean needCookie, boolean alive, String reason) {
        static CookiePrecheck notNeeded() {
            return new CookiePrecheck(false, true, "");
        }

        static CookiePrecheck dead(String reason) {
            return new CookiePrecheck(true, false, reason);
        }

        static CookiePrecheck ok() {
            return new CookiePrecheck(true, true, "");
        }
    }

    public record ProbeCookieConfig(boolean configured, boolean enabled, String cookieHeader,
                                    String hostSuffixes, String remark, String updateBy, Object updateTime) {
    }

    /** 后台读取探测 Cookie（含原文，便于编辑）。 */
    public ProbeCookieConfig getProbeCookieConfig() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT cookie_header, host_suffixes, enabled, remark, update_by, update_time
                FROM ops_link_monitor_probe_cookie WHERE id = 1 LIMIT 1
                """);
        if (rows.isEmpty()) {
            return new ProbeCookieConfig(false, true, "", DEFAULT_COOKIE_HOSTS, "", "", null);
        }
        Map<String, Object> r = rows.getFirst();
        String cookie = r.get("cookie_header") == null ? "" : String.valueOf(r.get("cookie_header"));
        String hosts = r.get("host_suffixes") == null ? DEFAULT_COOKIE_HOSTS : String.valueOf(r.get("host_suffixes"));
        if (!StringUtils.hasText(hosts)) {
            hosts = DEFAULT_COOKIE_HOSTS;
        }
        boolean enabled = r.get("enabled") == null || ((Number) r.get("enabled")).intValue() == 1;
        String remark = r.get("remark") == null ? "" : String.valueOf(r.get("remark"));
        String updateBy = r.get("update_by") == null ? "" : String.valueOf(r.get("update_by"));
        return new ProbeCookieConfig(StringUtils.hasText(cookie.trim()), enabled, cookie, hosts.trim(),
                remark, updateBy, r.get("update_time"));
    }

    public Map<String, Object> probeCookieStatusMap() {
        ProbeCookieConfig c = getProbeCookieConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", c.configured());
        m.put("enabled", c.enabled());
        m.put("cookieHeader", c.cookieHeader() == null ? "" : c.cookieHeader());
        m.put("hostSuffixes", c.hostSuffixes());
        m.put("remark", c.remark());
        m.put("updateBy", c.updateBy());
        m.put("updateTime", c.updateTime());
        m.put("cookiePreview", previewCookie(c.cookieHeader()));
        return m;
    }

    /** 保存可选探测 Cookie（浏览器导出会话头，不存密码）。空 cookie 视为清空。 */
    public void saveProbeCookie(String cookieRaw, String hostSuffixes, Boolean enabled, String remark, String operator) {
        String cookie = normalizeCookiePaste(cookieRaw);
        String hosts = StringUtils.hasText(hostSuffixes) ? hostSuffixes.trim() : DEFAULT_COOKIE_HOSTS;
        if (hosts.length() > 500) {
            throw new BusinessException("适用域名过长");
        }
        int en = enabled == null || enabled ? 1 : 0;
        String rem = remark == null ? "" : remark.trim();
        if (rem.length() > 500) {
            rem = rem.substring(0, 500);
        }
        Integer cnt = jdbc.queryForObject("SELECT COUNT(1) FROM ops_link_monitor_probe_cookie WHERE id = 1", Integer.class);
        if (cnt != null && cnt > 0) {
            // 更新 Cookie 后清空当日失效告警标记，便于再次失效时能重新通知
            jdbc.update("""
                    UPDATE ops_link_monitor_probe_cookie
                    SET cookie_header = ?, host_suffixes = ?, enabled = ?, remark = ?,
                        last_cookie_alert_at = NULL, update_by = ?, update_time = NOW()
                    WHERE id = 1
                    """, cookie, hosts, en, rem, operator == null ? "" : operator);
        } else {
            jdbc.update("""
                    INSERT INTO ops_link_monitor_probe_cookie
                      (id, cookie_header, host_suffixes, enabled, remark, update_by, update_time)
                    VALUES (1, ?, ?, ?, ?, ?, NOW())
                    """, cookie, hosts, en, rem, operator == null ? "" : operator);
        }
        invalidateProbeCookieHealthCache();
    }

    public void clearProbeCookie(String operator) {
        saveProbeCookie("", DEFAULT_COOKIE_HOSTS, true, "", operator);
    }

    public void invalidateProbeCookieHealthCache() {
        cookieHealthCache.set(null);
    }

    /**
     * 若已配置且启用、目标 host 匹配后缀，返回规范化 Cookie 头；否则空串。
     */
    public String resolveProbeCookieForUrl(String url) {
        ProbeCookieConfig c = getProbeCookieConfig();
        if (!c.configured() || !c.enabled() || !StringUtils.hasText(c.cookieHeader())) {
            return "";
        }
        String host;
        try {
            host = URI.create(url.trim()).getHost();
        } catch (Exception ex) {
            return "";
        }
        if (!StringUtils.hasText(host)) {
            return "";
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        for (String rawSuffix : c.hostSuffixes().split("[,;\\s]+")) {
            if (!StringUtils.hasText(rawSuffix)) {
                continue;
            }
            String suffix = rawSuffix.trim().toLowerCase(Locale.ROOT);
            while (suffix.startsWith(".")) {
                suffix = suffix.substring(1);
            }
            if (!StringUtils.hasText(suffix)) {
                continue;
            }
            if (hostLower.equals(suffix) || hostLower.endsWith("." + suffix)) {
                return c.cookieHeader().trim();
            }
        }
        return "";
    }

    static String normalizeCookiePaste(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String s = raw.trim();
        if (s.regionMatches(true, 0, "Cookie:", 0, 7)) {
            s = s.substring(7).trim();
        }
        // EditThisCookie / 部分扩展导出的 JSON 数组
        if (s.startsWith("[")) {
            try {
                JsonNode arr = JSON.readTree(s);
                if (arr.isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode n : arr) {
                        String name = n.path("name").asText("");
                        String value = n.path("value").asText("");
                        if (!StringUtils.hasText(name)) {
                            continue;
                        }
                        if (!sb.isEmpty()) {
                            sb.append("; ");
                        }
                        sb.append(name.trim()).append("=").append(value);
                    }
                    return sb.toString();
                }
            } catch (Exception ignored) {
                // 按普通 Cookie 串继续
            }
        }
        return s.replace("\r", " ").replace("\n", " ").trim();
    }

    static String previewCookie(String cookie) {
        if (!StringUtils.hasText(cookie)) {
            return "";
        }
        String t = cookie.trim();
        if (t.length() <= 24) {
            return t.substring(0, Math.min(8, t.length())) + "…";
        }
        return t.substring(0, 16) + "…（共 " + t.length() + " 字符）";
    }

    public List<String> extractUrls(String text) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return out;
        }
        Matcher m = URL_PAT.matcher(text);
        while (m.find()) {
            String u = normalizeUrl(m.group());
            if (StringUtils.hasText(u) && !out.contains(u)) {
                out.add(u);
            }
        }
        return out;
    }

    public String normalizeUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String u = raw.trim();
        while (u.endsWith(")") || u.endsWith("。") || u.endsWith(",") || u.endsWith("，") || u.endsWith("；")) {
            u = u.substring(0, u.length() - 1);
        }
        if (!(u.startsWith("http://") || u.startsWith("https://"))) {
            return "";
        }
        if (u.length() > 900) {
            throw new BusinessException("链接过长");
        }
        String lower = u.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://127.") || lower.startsWith("https://127.")
                || lower.contains("://localhost") || lower.contains("://0.0.0.0")
                || lower.contains("://169.254.") || lower.contains("://metadata")) {
            throw new BusinessException("不允许监控内网/本机地址");
        }
        return u;
    }

    public long addMonitor(String url, String openConversationId, String conversationType,
                           String creatorStaffId, Long creatorUserId, String source, String createBy) {
        String normalized = normalizeUrl(url);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("无效链接，请提供 http/https 完整地址");
        }
        String hash = sha256(normalized);
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM ops_link_monitor
                WHERE url_hash = ? AND status = 'ACTIVE' AND is_active = 1
                """, Integer.class, hash);
        if (exists != null && exists > 0) {
            throw new BusinessException("该链接已在监控列表中：" + normalized);
        }
        // create_by 存可读人名，禁止直接落钉钉 staffId/工号
        String creatorLabel = resolveCreatorDisplayName(creatorStaffId, creatorUserId, createBy);
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO ops_link_monitor (url, url_hash, scope_type, source, open_conversation_id,
                      conversation_type, creator_staff_id, creator_user_id, status, interval_sec, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, normalized);
            ps.setString(2, hash);
            ps.setString(3, RobotProductRules.SCOPE_GLOBAL);
            ps.setString(4, source == null ? "robot" : source);
            ps.setString(5, openConversationId == null ? "" : openConversationId);
            ps.setString(6, conversationType == null ? "1" : conversationType);
            ps.setString(7, creatorStaffId == null ? "" : creatorStaffId);
            if (creatorUserId == null) {
                ps.setObject(8, null);
            } else {
                ps.setLong(8, creatorUserId);
            }
            ps.setInt(9, RobotProductRules.DEFAULT_INTERVAL_SEC);
            ps.setString(10, creatorLabel);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0L : key.longValue();
    }

    public List<Map<String, Object>> listActive(int limit) {
        return jdbc.queryForList("""
                SELECT id, url, name, status, open_conversation_id, conversation_type, interval_sec,
                       last_check_at, last_ok, last_status_code, last_error, create_by, create_time, source
                FROM ops_link_monitor
                WHERE status = 'ACTIVE' AND is_active = 1
                ORDER BY id DESC
                LIMIT ?
                """, Math.max(1, Math.min(limit, 200)));
    }

    /** 最近一次探测失败的 ACTIVE 监控（last_ok = 0）。 */
    public List<Map<String, Object>> listAbnormal(int limit) {
        return jdbc.queryForList("""
                SELECT id, url, name, status, open_conversation_id, conversation_type, interval_sec,
                       last_check_at, last_ok, last_status_code, last_error, create_by, create_time, source
                FROM ops_link_monitor
                WHERE status = 'ACTIVE' AND is_active = 1 AND last_ok = 0
                ORDER BY last_check_at DESC, id DESC
                LIMIT ?
                """, Math.max(1, Math.min(limit, 200)));
    }

    public record ProbeFailItem(long id, String url, String error) {
    }

    public record ProbeBatchSummary(int total, int okCount, int failCount, List<ProbeFailItem> failures) {
    }

    /**
     * 对当前全部 ACTIVE 监控即时跑一轮探测（用于「是否有异常」意图）。
     * {@code forceAlert=false}：失败仍按自然日去重告警，避免用户多问一次就刷屏。
     */
    public ProbeBatchSummary probeAllActiveNow(boolean forceAlert) {
        List<Map<String, Object>> active = listActive(200);
        // 批量前先做一次 Cookie 预检，后续 probeOne 复用缓存
        for (Map<String, Object> row : active) {
            String url = row.get("url") == null ? "" : String.valueOf(row.get("url"));
            if (StringUtils.hasText(resolveProbeCookieForUrl(url))) {
                precheckProbeCookieForUrl(url);
                break;
            }
        }
        int okCount = 0;
        List<ProbeFailItem> failures = new ArrayList<>();
        for (Map<String, Object> row : active) {
            long id = ((Number) row.get("id")).longValue();
            String url = row.get("url") == null ? "" : String.valueOf(row.get("url"));
            ProbeResult r = probeOne(id, forceAlert);
            if (r.ok()) {
                okCount++;
            } else {
                String err = StringUtils.hasText(r.error()) ? r.error() : "探测失败";
                failures.add(new ProbeFailItem(id, url, err));
            }
        }
        return new ProbeBatchSummary(active.size(), okCount, failures.size(), failures);
    }

    public void softDelete(long id, String operatorStaffId, boolean fromGroup) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, open_conversation_id, conversation_type, creator_staff_id, status
                FROM ops_link_monitor WHERE id = ? AND is_active = 1 LIMIT 1
                """, id);
        if (rows.isEmpty()) {
            throw new BusinessException("监控不存在");
        }
        Map<String, Object> row = rows.getFirst();
        if (!"ACTIVE".equals(String.valueOf(row.get("status")))) {
            throw new BusinessException("该监控已停用");
        }
        // 群内任何人可删：群场景来源不校验创建人；后台走权限注解
        if (fromGroup && !RobotProductRules.GROUP_ANYONE_CAN_DELETE) {
            String creator = String.valueOf(row.get("creator_staff_id"));
            if (StringUtils.hasText(operatorStaffId) && !operatorStaffId.equals(creator)) {
                throw new BusinessException("仅创建人可删除");
            }
        }
        jdbc.update("""
                UPDATE ops_link_monitor SET status = 'DELETED', is_active = 0, update_time = NOW()
                WHERE id = ?
                """, id);
    }

    public void pause(long id) {
        jdbc.update("UPDATE ops_link_monitor SET status = 'PAUSED', update_time = NOW() WHERE id = ? AND is_active = 1", id);
    }

    public void resume(long id) {
        jdbc.update("UPDATE ops_link_monitor SET status = 'ACTIVE', update_time = NOW() WHERE id = ? AND is_active = 1", id);
    }

    /**
     * 自然日尚未探测的 ACTIVE 目标（按库日期 CURDATE，连接时区应为 Asia/Shanghai）。
     */
    public List<Long> dueMonitorIdsForNaturalDay() {
        return jdbc.query("""
                SELECT id FROM ops_link_monitor
                WHERE status = 'ACTIVE' AND is_active = 1
                  AND (last_check_at IS NULL OR DATE(last_check_at) < CURDATE())
                ORDER BY id ASC
                LIMIT 200
                """, (rs, i) -> rs.getLong(1));
    }

    public record ProbeResult(boolean ok, String error, boolean alerted) {
    }

    /** 定时探测：失败时按自然日去重告警。 */
    public void probeOne(long monitorId) {
        probeOne(monitorId, false);
    }

    /**
     * @param forceAlert true=后台手动探测：失败必告警（不受当日去重限制）；
     *                   优先推绑定群并 @ 创建人，群不可用/无法 @ 则钉钉通知 wangfangyang。
     */
    public ProbeResult probeOne(long monitorId, boolean forceAlert) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, url, timeout_ms, open_conversation_id, conversation_type,
                       creator_staff_id, creator_user_id, create_by
                FROM ops_link_monitor WHERE id = ? AND is_active = 1 LIMIT 1
                """, monitorId);
        if (rows.isEmpty()) {
            return new ProbeResult(false, "监控不存在", false);
        }
        Map<String, Object> row = rows.getFirst();
        String url = String.valueOf(row.get("url"));
        int timeout = row.get("timeout_ms") == null ? 15000 : ((Number) row.get("timeout_ms")).intValue();
        long start = System.currentTimeMillis();

        // 需要带 Cookie 的域名：先预检会话；失效则跳过本条探测，只告警更新 Cookie
        CookiePrecheck pre = precheckProbeCookieForUrl(url);
        if (pre.needCookie() && !pre.alive()) {
            String skipErr = StringUtils.hasText(pre.reason())
                    ? pre.reason()
                    : "探测 Cookie 已失效，已跳过探测";
            int latency = (int) (System.currentTimeMillis() - start);
            jdbc.update("""
                    INSERT INTO ops_link_monitor_check_log (monitor_id, checked_at, ok, http_status, latency_ms, error_msg)
                    VALUES (?, NOW(), 0, NULL, ?, ?)
                    """, monitorId, latency, skipErr);
            // 不改 last_ok，避免把「Cookie 挂了」记成「笔记挂了」
            jdbc.update("""
                    UPDATE ops_link_monitor SET last_check_at = NOW(), last_error = ?, update_time = NOW()
                    WHERE id = ?
                    """, skipErr, monitorId);
            boolean alerted = maybeAlertProbeCookieExpired(monitorId, url, skipErr);
            return new ProbeResult(false, skipErr, alerted);
        }

        boolean ok = false;
        Integer status = null;
        String err = "";
        boolean withAuthCookie = pre.needCookie();
        try {
            String probeCookie = withAuthCookie ? resolveProbeCookieForUrl(url) : "";
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(3000, timeout)))
                    .GET()
                    .header("User-Agent", BROWSER_UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            if (withAuthCookie && StringUtils.hasText(probeCookie)) {
                reqBuilder.header("Cookie", probeCookie);
            }
            HttpResponse<byte[]> resp = http.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            status = resp.statusCode();
            if (status == 404 || status == 403 || status == 410 || status == 451 || status >= 500) {
                err = "HTTP " + status;
            } else if (status < 200 || status >= 400) {
                err = "HTTP " + status;
            } else {
                // 失效笔记常见：302→首页/404/其它内容，落地仍 HTTP 200，不能只看状态码。
                // 无 Cookie：跳登录页 ≠ 失效；有 Cookie：登录墙可视为会话失效或内容不可见。
                boolean contentDeep = isContentDeepLink(url);
                String soft = detectSoftRedirect(url, resp.uri(), withAuthCookie);
                if (StringUtils.hasText(soft)) {
                    err = soft;
                } else {
                    String body = decodeBody(resp.body(), resp.headers().firstValue("Content-Type").orElse(""));
                    String unavailable = detectUnavailableHint(body, contentDeep, withAuthCookie);
                    if (StringUtils.hasText(unavailable)) {
                        err = contentDeep
                                ? "笔记链接已失效（" + unavailable + "）"
                                : "页面提示不可用：" + unavailable;
                    } else {
                        ok = true;
                    }
                }
            }
        } catch (Exception ex) {
            err = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (err.length() > 480) {
                err = err.substring(0, 480);
            }
        }
        int latency = (int) (System.currentTimeMillis() - start);
        jdbc.update("""
                INSERT INTO ops_link_monitor_check_log (monitor_id, checked_at, ok, http_status, latency_ms, error_msg)
                VALUES (?, NOW(), ?, ?, ?, ?)
                """, monitorId, ok ? 1 : 0, status, latency, err);
        jdbc.update("""
                UPDATE ops_link_monitor SET last_check_at = NOW(), last_ok = ?, last_status_code = ?,
                  last_error = ?, update_time = NOW()
                WHERE id = ?
                """, ok ? 1 : 0, status, err, monitorId);
        boolean alerted = false;
        if (!ok) {
            if (withAuthCookie && looksLikeProbeCookieExpired(err)) {
                // 预检漏网：仍跳登录 → 只催更新 Cookie，不发「链接失效」告警
                alerted = maybeAlertProbeCookieExpired(monitorId, url, err);
                invalidateProbeCookieHealthCache();
                cookieHealthCache.set(new CookieHealthCache(sha256(resolveProbeCookieForUrl(url)), false, err,
                        System.currentTimeMillis()));
            } else {
                alerted = alertFailure(monitorId, url, err, row, forceAlert);
            }
        }
        return new ProbeResult(ok, err, alerted);
    }

    /**
     * 对需要 Cookie 的 URL：先探测会话是否仍有效。
     * 未配置 / 域名不匹配 → 不需要 Cookie，直接放行。
     */
    CookiePrecheck precheckProbeCookieForUrl(String url) {
        String cookie = resolveProbeCookieForUrl(url);
        if (!StringUtils.hasText(cookie)) {
            return CookiePrecheck.notNeeded();
        }
        String fp = sha256(cookie);
        long now = System.currentTimeMillis();
        CookieHealthCache cached = cookieHealthCache.get();
        if (cached != null
                && fp.equals(cached.cookieFp())
                && now - cached.checkedAtMs() < COOKIE_HEALTH_CACHE_MS) {
            return cached.alive() ? CookiePrecheck.ok() : CookiePrecheck.dead(cached.reason());
        }
        ProbeCookieConfig cfg = getProbeCookieConfig();
        String checkUrl = buildCookieHealthCheckUrl(cfg.hostSuffixes());
        String reason = pingProbeCookieSession(cookie, checkUrl);
        boolean alive = !StringUtils.hasText(reason);
        String deadReason = alive ? "" : ("探测 Cookie 已失效，已跳过探测（" + reason + "）");
        cookieHealthCache.set(new CookieHealthCache(fp, alive, deadReason, now));
        return alive ? CookiePrecheck.ok() : CookiePrecheck.dead(deadReason);
    }

    static String buildCookieHealthCheckUrl(String hostSuffixes) {
        String suffix = "xiaohongshu.com";
        if (StringUtils.hasText(hostSuffixes)) {
            for (String raw : hostSuffixes.split("[,;\\s]+")) {
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                String s = raw.trim().toLowerCase(Locale.ROOT);
                while (s.startsWith(".")) {
                    s = s.substring(1);
                }
                if (StringUtils.hasText(s)) {
                    suffix = s;
                    break;
                }
            }
        }
        return "https://www." + suffix + "/";
    }

    /**
     * @return 空串=会话可用；非空=失效原因
     */
    private String pingProbeCookieSession(String cookieHeader, String checkUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(checkUrl))
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .header("User-Agent", BROWSER_UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Cookie", cookieHeader)
                    .build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            int code = resp.statusCode();
            if (code == 401 || code == 403) {
                return "健康检查 HTTP " + code;
            }
            URI finalUri = resp.uri();
            String path = finalUri == null ? "/" : normalizePath(finalUri.getPath()).toLowerCase(Locale.ROOT);
            String query = "";
            if (finalUri != null && StringUtils.hasText(finalUri.getRawQuery())) {
                try {
                    query = URLDecoder.decode(finalUri.getRawQuery(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                } catch (Exception ex) {
                    query = finalUri.getRawQuery().toLowerCase(Locale.ROOT);
                }
            }
            if (isLoginLanding(path, query)) {
                return "健康检查跳转到登录页";
            }
            // 首页正文常有「登录」入口文案，不能当会话失效；只认 URL 落到登录页 / 401·403
            return "";
        } catch (Exception ex) {
            // 网络异常时不阻断业务探测（避免误杀）
            log.warn("probe cookie health check error: {}", ex.getMessage());
            return "";
        }
    }

    static boolean looksLikeProbeCookieExpired(String err) {
        if (!StringUtils.hasText(err)) {
            return false;
        }
        return err.contains("Cookie 可能失效")
                || err.contains("已跳转到登录页，Cookie");
    }

    /**
     * 已配置探测 Cookie 却仍落登录墙：钉钉通知 wangfangyang 去后台更新 Cookie。
     * 按自然日去重（全局限一次，避免批量探测刷屏）。
     *
     * @return 是否实际发出了 Cookie 失效告警
     */
    private boolean maybeAlertProbeCookieExpired(long monitorId, String url, String err) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT last_cookie_alert_at FROM ops_link_monitor_probe_cookie WHERE id = 1 LIMIT 1
                    """);
            if (!rows.isEmpty()) {
                Object last = rows.getFirst().get("last_cookie_alert_at");
                if (last instanceof java.sql.Timestamp ts
                        && ts.toLocalDateTime().toLocalDate().equals(LocalDateTime.now().toLocalDate())) {
                    return false;
                }
            }
            String content = """
                    【链接监控】探测 Cookie 已失效，本轮相关探测已跳过
                    请到后台「机器人 → 链接监控 → 探测 Cookie」用浏览器重新登录后粘贴更新。
                    触发监控 #%d
                    URL：%s
                    原因：%s
                    时间：%s
                    """.formatted(monitorId, url, err, LocalDateTime.now()).trim();
            notifyByUsername(monitorId, content, RobotProductRules.DM_ALERT_USERNAME);
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM ops_link_monitor_probe_cookie WHERE id = 1", Integer.class);
            if (cnt != null && cnt > 0) {
                jdbc.update("UPDATE ops_link_monitor_probe_cookie SET last_cookie_alert_at = NOW() WHERE id = 1");
            } else {
                jdbc.update("""
                        INSERT INTO ops_link_monitor_probe_cookie (id, cookie_header, last_cookie_alert_at, update_time)
                        VALUES (1, '', NOW(), NOW())
                        """);
            }
            return true;
        } catch (Exception ex) {
            log.warn("probe cookie expired alert failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 跟随重定向后，落地页是否仍是「原来那条内容」。
     * 通则：原链接能识别出内容 id 时，最终 URL 必须仍指向同一内容；
     * 落到泛首页（如 /explore）、404、或其它内容 id → 失效。
     * 登录页：无 Cookie 时不当失败；带 Cookie 仍跳登录 → 会话失效/不可见，判失败。
     */
    static String detectSoftRedirect(String originalUrl, URI finalUri) {
        return detectSoftRedirect(originalUrl, finalUri, false);
    }

    static String detectSoftRedirect(String originalUrl, URI finalUri, boolean withAuthCookie) {
        if (finalUri == null || !StringUtils.hasText(originalUrl)) {
            return "";
        }
        String finalPath = normalizePath(finalUri.getPath());
        String finalPathLower = finalPath.toLowerCase(Locale.ROOT);
        String queryDecoded = "";
        if (StringUtils.hasText(finalUri.getRawQuery())) {
            try {
                queryDecoded = URLDecoder.decode(finalUri.getRawQuery(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                queryDecoded = finalUri.getRawQuery();
            }
        }
        String queryLower = queryDecoded.toLowerCase(Locale.ROOT);

        String origPath = "";
        try {
            origPath = normalizePath(URI.create(originalUrl.trim()).getPath());
        } catch (Exception ignored) {
            // keep empty
        }
        String contentId = extractContentId(origPath);
        boolean expectNote = isContentDeepLink(originalUrl)
                || StringUtils.hasText(contentId)
                || isShareOrShortLink(originalUrl);
        String failPrefix = expectNote ? "笔记链接已失效" : "链接已失效";

        if (finalPathLower.equals("/404") || finalPathLower.startsWith("/404/") || finalPathLower.contains("/404.")) {
            String hint = extractQueryErrorMsg(queryDecoded);
            if (StringUtils.hasText(hint)) {
                return failPrefix + "（" + hint + "）";
            }
            return failPrefix + "（已跳转到404页）";
        }
        if (isLoginLanding(finalPathLower, queryLower)) {
            if (withAuthCookie) {
                return failPrefix + "（已跳转到登录页，Cookie 可能失效）";
            }
            // 无凭证：机房 IP 常被拦，不按内容 id 丢失判死
            return "";
        }

        // —— 核心通则：有内容 id 时，落地必须仍是同一条内容 ——
        if (StringUtils.hasText(contentId)) {
            String finalId = extractContentId(finalPath);
            if (!contentId.equalsIgnoreCase(finalId)) {
                return failPrefix + "（已不是原笔记内容）";
            }
            // 同 id 仍在，视为未跑偏（正文提示另检）
            return "";
        }

        // 短链/分享链无内容 id：落地若是泛首页、信息流入口等，也不是原笔记
        if (expectNote && isGenericListingLanding(finalPathLower)) {
            return failPrefix + "（已不是原笔记内容）";
        }
        return "";
    }

    static boolean isContentDeepLink(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            String path = normalizePath(URI.create(url.trim()).getPath());
            String p = path.toLowerCase(Locale.ROOT);
            return p.contains("/discovery/item/")
                    || p.matches(".*/explore/[a-z0-9_-]{6,}.*")
                    || p.matches(".*/(item|note|detail|post|status|aweme|video)/[^/]+.*");
        } catch (Exception ex) {
            return false;
        }
    }

    /** 短链/分享入口（最终会跳到正式内容页；失效时常落到站点首页）。 */
    static boolean isShareOrShortLink(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            String path = normalizePath(u.getPath()).toLowerCase(Locale.ROOT);
            if (host.contains("xhslink.com") || host.contains("xhs.cn")
                    || host.contains("douyin.com") && (path.startsWith("/v/") || path.startsWith("/share/"))
                    || host.contains("b23.tv") || host.contains("t.cn")) {
                return true;
            }
            return path.matches(".*/(s|m|share)/[^/]+.*");
        } catch (Exception ex) {
            return false;
        }
    }

    /** 登录/鉴权落地页（各站点路径不一，按常见段匹配）。 */
    static boolean isLoginLanding(String pathLower, String queryLower) {
        String p = normalizePath(pathLower).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(p)) {
            return false;
        }
        if (p.matches(".*/(login|signin|sign_in|sign-in|passport)(/.*)?$")) {
            return true;
        }
        if (p.contains("/passport/")
                || p.contains("/website-login")
                || p.contains("/user/login")
                || p.contains("/account/login")
                || p.contains("/auth/login")
                || p.contains("/web-login")) {
            return true;
        }
        return p.contains("/login")
                && (queryLower.contains("redirect") || queryLower.contains("return") || queryLower.contains("callback"));
    }

    /**
     * 泛首页 / 信息流入口（不是某一条笔记）。
     * 例：小红书 {@code /explore}、站点根路径等。
     */
    static boolean isGenericListingLanding(String pathLower) {
        String p = normalizePath(pathLower).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(p) || "/".equals(p)) {
            return true;
        }
        return p.equals("/explore")
                || p.equals("/index")
                || p.equals("/home")
                || p.equals("/feed")
                || p.equals("/recommend")
                || p.equals("/index.html")
                || p.equals("/home.html");
    }

    static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String p = path.trim();
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.isEmpty() ? "/" : p;
    }

    /** 从内容深链路径取出内容 id（explore/xxx、item/xxx 等末段）。 */
    static String extractContentId(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        Matcher m = Pattern.compile(
                "(?i)/(?:discovery/item|explore|item|note|detail|post|status|aweme|video)/([A-Za-z0-9_-]{6,})")
                .matcher(path);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private static String extractQueryErrorMsg(String queryDecoded) {
        if (!StringUtils.hasText(queryDecoded)) {
            return "";
        }
        for (String part : queryDecoded.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq);
            String val = part.substring(eq + 1);
            if ("error_msg".equalsIgnoreCase(key) && StringUtils.hasText(val)) {
                return val.length() > 80 ? val.substring(0, 80) : val;
            }
        }
        if (queryDecoded.contains("无法浏览")) {
            return "当前笔记暂时无法浏览";
        }
        return "";
    }

    /**
     * 识别页面正文中的「不可用」信号。
     * 无 Cookie：笔记深链只认删帖/404 强信号；有 Cookie：额外认登录墙。
     */
    static String detectUnavailableHint(String body) {
        return detectUnavailableHint(body, false, false);
    }

    static String detectUnavailableHint(String body, boolean contentDeep) {
        return detectUnavailableHint(body, contentDeep, false);
    }

    static String detectUnavailableHint(String body, boolean contentDeep, boolean withAuthCookie) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String text = body.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ");
        String lower = text.toLowerCase(Locale.ROOT);
        // contentDeep 且无 Cookie：登录页常夹带「无法查看」文案，只认删帖/不存在类强信号
        boolean strictNote = contentDeep && !withAuthCookie;
        String[][] patterns = strictNote
                ? new String[][]{
                {"你访问的页面不见了", "你访问的页面不见了"},
                {"页面不见了", "页面不见了"},
                {"当前笔记暂时无法浏览", "当前笔记暂时无法浏览"},
                {"暂时无法浏览", "暂时无法浏览"},
                {"文章不存在", "文章不存在"},
                {"笔记不存在", "笔记不存在"},
                {"内容不存在", "内容不存在"},
                {"页面不存在", "页面不存在"},
                {"不存在或已被删除", "不存在或已被删除"},
                {"已被删除", "已被删除"},
                {"文章被删", "文章被删"},
                {"笔记被删", "笔记被删"},
                {"page not found", "page not found"},
                {"404 not found", "404 not found"},
                {"错误码：404", "404"},
                {"错误码:404", "404"},
                {"status code 404", "404"},
        }
                : new String[][]{
                {"你访问的页面不见了", "你访问的页面不见了"},
                {"页面不见了", "页面不见了"},
                {"当前笔记暂时无法浏览", "当前笔记暂时无法浏览"},
                {"暂时无法浏览", "暂时无法浏览"},
                {"文章不存在", "文章不存在"},
                {"笔记不存在", "笔记不存在"},
                {"内容不存在", "内容不存在"},
                {"页面不存在", "页面不存在"},
                {"不存在或已被删除", "不存在或已被删除"},
                {"已被删除", "已被删除"},
                {"文章被删", "文章被删"},
                {"笔记被删", "笔记被删"},
                {"该内容无法查看", "该内容无法查看"},
                {"内容无法查看", "内容无法查看"},
                {"无法查看", "无法查看"},
                {"无权限", "无权限"},
                {"没有权限", "没有权限"},
                {"权限不足", "权限不足"},
                {"无权访问", "无权访问"},
                {"无访问权限", "无访问权限"},
                {"access denied", "access denied"},
                {"page not found", "page not found"},
                {"404 not found", "404 not found"},
                {"错误码：404", "404"},
                {"错误码:404", "404"},
                {"status code 404", "404"},
        };
        for (String[] p : patterns) {
            if (text.contains(p[0]) || lower.contains(p[0].toLowerCase(Locale.ROOT))) {
                return p[1];
            }
        }
        if (contentDeep && withAuthCookie) {
            String[][] loginWall = {
                    {"请先登录", "需登录才能查看（Cookie 可能失效）"},
                    {"登录后查看", "需登录才能查看（Cookie 可能失效）"},
                    {"登录后可见", "需登录才能查看（Cookie 可能失效）"},
                    {"登录后继续", "需登录才能查看（Cookie 可能失效）"},
                    {"扫码登录", "需登录才能查看（Cookie 可能失效）"},
                    {"手机号登录", "需登录才能查看（Cookie 可能失效）"},
                    {"立即登录", "需登录才能查看（Cookie 可能失效）"},
                    {"去登录", "需登录才能查看（Cookie 可能失效）"},
                    {"sign in to continue", "需登录才能查看（Cookie 可能失效）"},
                    {"please log in", "需登录才能查看（Cookie 可能失效）"},
                    {"please login", "需登录才能查看（Cookie 可能失效）"},
            };
            for (String[] p : loginWall) {
                if (text.contains(p[0]) || lower.contains(p[0].toLowerCase(Locale.ROOT))) {
                    return p[1];
                }
            }
            Matcher titleM = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(body);
            if (titleM.find()) {
                String title = titleM.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                if (title.contains("登录") || title.contains("login") || title.contains("sign in")) {
                    return "需登录才能查看（Cookie 可能失效）";
                }
            }
        }
        return "";
    }

    private static String decodeBody(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int max = Math.min(bytes.length, 256 * 1024);
        byte[] slice = max == bytes.length ? bytes : java.util.Arrays.copyOf(bytes, max);
        Charset charset = StandardCharsets.UTF_8;
        if (StringUtils.hasText(contentType)) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("charset=gbk") || ct.contains("charset=gb2312")) {
                charset = Charset.forName("GBK");
            }
        }
        return new String(slice, charset);
    }

    /**
     * 告警。
     * <ul>
     *   <li>定时：同自然日去重；有绑定群则推群，并通知创建人（失败兜底 wangfangyang）</li>
     *   <li>后台手动（forceAlert）：不过滤当日；优先推绑定群并 @ 创建人；
     *       群不在/推送失败/无法解析创建人钉钉 userid → 钉钉通知 wangfangyang</li>
     * </ul>
     *
     * @return 是否实际发出了告警（含兜底）
     */
    private boolean alertFailure(long monitorId, String url, String err, Map<String, Object> row, boolean forceAlert) {
        if (!forceAlert) {
            LocalDateTime lastAlert = jdbc.query("""
                    SELECT last_alert_at FROM ops_link_monitor WHERE id = ?
                    """, rs -> rs.next() && rs.getTimestamp(1) != null
                    ? rs.getTimestamp(1).toLocalDateTime() : null, monitorId);
            // 同一自然日只告警一次
            if (lastAlert != null && lastAlert.toLocalDate().equals(LocalDateTime.now().toLocalDate())) {
                return false;
            }
        }
        String conversationId = row.get("open_conversation_id") == null ? "" : String.valueOf(row.get("open_conversation_id"));
        String conversationType = row.get("conversation_type") == null ? "1" : String.valueOf(row.get("conversation_type"));
        String creatorStaffId = row.get("creator_staff_id") == null ? "" : String.valueOf(row.get("creator_staff_id"));
        Long creatorUserId = row.get("creator_user_id") == null ? null : ((Number) row.get("creator_user_id")).longValue();
        String groupTitle = resolveGroupTitle(conversationId);

        StringBuilder contentSb = new StringBuilder();
        contentSb.append("【链接监控告警】链接不可用\n");
        contentSb.append("URL：").append(url).append("\n");
        contentSb.append("原因：").append(err).append("\n");
        contentSb.append("时间：").append(LocalDateTime.now()).append("\n");
        boolean isGroup = "2".equals(conversationType) && StringUtils.hasText(conversationId);
        if (isGroup) {
            contentSb.append("来源群：").append(StringUtils.hasText(groupTitle) ? groupTitle : conversationId);
        } else {
            contentSb.append("来源：").append(forceAlert ? "后台手动探测" : ("1".equals(conversationType) ? "单聊/后台" : "未知"));
        }
        String creatorName = resolveCreatorDisplayName(creatorStaffId, creatorUserId,
                row.get("create_by") == null ? "" : String.valueOf(row.get("create_by")));
        if (StringUtils.hasText(creatorName)) {
            contentSb.append("\n创建人：").append(creatorName);
        }
        String content = contentSb.toString();
        String atUserId = resolveDingUserId(creatorStaffId, creatorUserId);
        boolean alerted = false;

        if (forceAlert) {
            // 手动探测：群 + @创建人；否则兜底 wangfangyang
            if (isGroup) {
                List<String> atList = StringUtils.hasText(atUserId) ? List.of(atUserId) : List.of();
                List<String> atLabels = StringUtils.hasText(atUserId) ? List.of(creatorName) : List.of();
                DingTalkCalendarClient.NoticeCall groupCall = dingTalkCalendarClient.sendRobotGroupMarkdown(
                        conversationId, "链接监控告警", content.replace("\n", "  \n"), atList, atLabels);
                String targetLabel = StringUtils.hasText(groupTitle)
                        ? (groupTitle + "|" + conversationId)
                        : conversationId;
                String groupErr = groupCall.success() ? "" : (groupCall.message() == null ? "群消息失败" : groupCall.message());
                if (groupCall.success() && atList.isEmpty()) {
                    groupErr = "无法@创建人（无钉钉userid）";
                }
                boolean groupOk = groupCall.success() && !atList.isEmpty();
                writeAlert(monitorId, "GROUP", targetLabel, content, groupOk,
                        groupOk ? "" : (StringUtils.hasText(groupErr) ? groupErr : "群不可用或无法@创建人"));
                if (groupOk) {
                    alerted = true;
                } else {
                    // 群不在 / 推送失败 / 创建人不在群（无 userid 无法@）→ 通知 wangfangyang
                    notifyByUsername(monitorId, content + "\n（兜底：群告警失败或无法@创建人）",
                            RobotProductRules.DM_ALERT_USERNAME);
                    alerted = true;
                }
            } else {
                writeAlert(monitorId, "ADMIN", "backend-manual", content, true, "无绑定告警群，改通知兜底人");
                notifyByUsername(monitorId, content + "\n（兜底：监控未绑定告警群）",
                        RobotProductRules.DM_ALERT_USERNAME);
                alerted = true;
            }
        } else if (isGroup) {
            // 定时：告警推回当初添加监控的群，并通知创建人
            List<String> atList = StringUtils.hasText(atUserId) ? List.of(atUserId) : List.of();
            List<String> atLabels = StringUtils.hasText(atUserId) ? List.of(creatorName) : List.of();
            DingTalkCalendarClient.NoticeCall groupCall = dingTalkCalendarClient.sendRobotGroupMarkdown(
                    conversationId, "链接监控告警", content.replace("\n", "  \n"), atList, atLabels);
            String targetLabel = StringUtils.hasText(groupTitle)
                    ? (groupTitle + "|" + conversationId)
                    : conversationId;
            writeAlert(monitorId, "GROUP", targetLabel, content, groupCall.success(),
                    groupCall.success() ? "" : groupCall.message());
            notifyCreatorOrFallback(monitorId, content, creatorStaffId, creatorUserId);
            alerted = true;
        } else {
            writeAlert(monitorId, "ADMIN", "backend", content, true, "");
            notifyCreatorOrFallback(monitorId, content, creatorStaffId, creatorUserId);
            alerted = true;
        }
        jdbc.update("UPDATE ops_link_monitor SET last_alert_at = NOW() WHERE id = ?", monitorId);
        return alerted;
    }

    /** 解析监控绑定的群名称（robot_group_profile）。 */
    public String resolveGroupTitle(String openConversationId) {
        if (!StringUtils.hasText(openConversationId)) {
            return "";
        }
        String title = jdbc.query("""
                SELECT title FROM robot_group_profile
                WHERE open_conversation_id = ? AND is_active = 1 LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : null, openConversationId.trim());
        return title == null ? "" : title.trim();
    }

    private void notifyCreatorOrFallback(long monitorId, String content, String creatorStaffId, Long creatorUserId) {
        String dingUserId = resolveDingUserId(creatorStaffId, creatorUserId);
        String targetLabel = StringUtils.hasText(creatorStaffId) ? creatorStaffId : ("uid:" + creatorUserId);
        if (!StringUtils.hasText(dingUserId)) {
            notifyByUsername(monitorId, content, RobotProductRules.DM_ALERT_USERNAME);
            return;
        }
        try {
            DingTalkCalendarClient.NoticeCall call = dingTalkCalendarClient.notifyMarkdown(
                    dingUserId, "链接监控告警", content);
            writeAlert(monitorId, "USER", targetLabel, content, call.success(),
                    call.success() ? "" : call.message());
            if (!call.success()) {
                notifyByUsername(monitorId, content, RobotProductRules.DM_ALERT_USERNAME);
            }
        } catch (Exception ex) {
            writeAlert(monitorId, "USER", targetLabel, content, false, ex.getMessage());
            notifyByUsername(monitorId, content, RobotProductRules.DM_ALERT_USERNAME);
        }
    }

    /**
     * 解析创建人展示名：优先系统昵称/用户名，避免直接展示钉钉 staffId/工号。
     */
    public String resolveCreatorDisplayName(String staffId, Long userId, String fallback) {
        if (userId != null) {
            String byUid = jdbc.query("""
                    SELECT COALESCE(NULLIF(TRIM(nickname), ''), NULLIF(TRIM(username), ''))
                    FROM sys_user WHERE user_id = ? AND is_active = 1 LIMIT 1
                    """, rs -> rs.next() ? rs.getString(1) : null, userId);
            if (StringUtils.hasText(byUid)) {
                return byUid.trim();
            }
        }
        if (StringUtils.hasText(staffId)) {
            String byDing = jdbc.query("""
                    SELECT COALESCE(NULLIF(TRIM(u.nickname), ''), NULLIF(TRIM(u.username), ''))
                    FROM hr_user_dingtalk d
                    INNER JOIN sys_user u ON u.user_id = d.user_id AND u.is_active = 1
                    WHERE d.is_active = 1 AND d.dingtalk_user_id = ?
                    LIMIT 1
                    """, rs -> rs.next() ? rs.getString(1) : null, staffId.trim());
            if (StringUtils.hasText(byDing)) {
                return byDing.trim();
            }
        }
        if (StringUtils.hasText(fallback) && !looksLikeStaffId(fallback)) {
            return fallback.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return StringUtils.hasText(staffId) ? staffId.trim() : "";
    }

    /** 纯数字长串多半是工号/钉钉 userid，不宜当作「人名」展示。 */
    static boolean looksLikeStaffId(String s) {
        if (!StringUtils.hasText(s)) {
            return false;
        }
        String t = s.trim();
        return t.matches("\\d{5,}") || t.matches("[A-Za-z0-9_-]{16,}");
    }

    private String resolveDingUserId(String staffId, Long userId) {
        // 优先按系统用户绑定取钉钉 userid
        if (userId != null) {
            String byUid = jdbc.query("""
                    SELECT d.dingtalk_user_id
                    FROM hr_user_dingtalk d
                    INNER JOIN sys_user u ON u.user_id = d.user_id AND u.is_active = 1
                    WHERE d.is_active = 1 AND d.user_id = ?
                    LIMIT 1
                    """, rs -> rs.next() ? rs.getString(1) : null, userId);
            if (StringUtils.hasText(byUid)) {
                return byUid.trim();
            }
        }
        if (StringUtils.hasText(staffId)) {
            String byStaff = jdbc.query("""
                    SELECT d.dingtalk_user_id
                    FROM hr_user_dingtalk d
                    INNER JOIN sys_user u ON u.user_id = d.user_id AND u.is_active = 1
                    WHERE d.is_active = 1 AND d.dingtalk_user_id = ?
                    LIMIT 1
                    """, rs -> rs.next() ? rs.getString(1) : null, staffId.trim());
            if (StringUtils.hasText(byStaff)) {
                return byStaff.trim();
            }
            // Stream 入站 senderStaffId 通常即钉钉 userid
            return staffId.trim();
        }
        return "";
    }

    private void notifyByUsername(long monitorId, String content, String username) {
        try {
            List<Map<String, Object>> users = jdbc.queryForList("""
                    SELECT d.dingtalk_user_id AS dingtalk_user_id
                    FROM hr_user_dingtalk d
                    INNER JOIN sys_user u ON u.user_id = d.user_id AND u.is_active = 1
                    WHERE d.is_active = 1 AND u.username = ?
                    LIMIT 1
                    """, username);
            if (users.isEmpty()) {
                writeAlert(monitorId, "USER", username, content, false, "用户未绑定钉钉");
                return;
            }
            String dingUserId = String.valueOf(users.getFirst().get("dingtalk_user_id"));
            DingTalkCalendarClient.NoticeCall call = dingTalkCalendarClient.notifyMarkdown(
                    dingUserId, "链接监控告警", content);
            writeAlert(monitorId, "USER", username, content, call.success(),
                    call.success() ? "" : call.message());
        } catch (Exception ex) {
            writeAlert(monitorId, "USER", username, content, false, ex.getMessage());
        }
    }

    private void writeAlert(long monitorId, String channel, String target, String content, boolean success, String error) {
        jdbc.update("""
                INSERT INTO ops_link_monitor_alert_log (monitor_id, channel, target, content, success, error_msg)
                VALUES (?, ?, ?, ?, ?, ?)
                """, monitorId, channel, target == null ? "" : target, content, success ? 1 : 0,
                error == null ? "" : error);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
