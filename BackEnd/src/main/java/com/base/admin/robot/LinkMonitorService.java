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
    /** 自行跟随的跳转上限。JDK 在跨主机 302 时会丢掉 Cookie，短链会因此被误判成登录失效。 */
    private static final int MAX_PROBE_REDIRECTS = 6;
    private final JdbcTemplate jdbc;
    private final DingTalkCalendarClient dingTalkCalendarClient;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private record FetchedPage(int status, URI uri, byte[] body, String contentType, boolean cookieOnFinal) {
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
    }

    public void clearProbeCookie(String operator) {
        saveProbeCookie("", DEFAULT_COOKIE_HOSTS, true, "", operator);
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
        if (!hostMatchesCookieSuffix(host, c.hostSuffixes())) {
            return "";
        }
        return c.cookieHeader().trim();
    }

    static boolean hostMatchesCookieSuffix(String host, String hostSuffixes) {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(hostSuffixes)) {
            return false;
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        for (String rawSuffix : hostSuffixes.split("[,;\\s]+")) {
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
                return true;
            }
        }
        return false;
    }

    /** 把浏览器 Cookie 头拆成有序名值，供跳转链路上按主机重放。 */
    static Map<String, String> parseCookiePairs(String cookieHeader) {
        Map<String, String> jar = new LinkedHashMap<>();
        if (!StringUtils.hasText(cookieHeader)) {
            return jar;
        }
        for (String part : cookieHeader.split(";")) {
            String item = part.trim();
            if (!StringUtils.hasText(item)) {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = item.substring(0, eq).trim();
            String value = item.substring(eq + 1).trim();
            if (StringUtils.hasText(name) && isSafeCookieToken(name) && isSafeCookieToken(value)) {
                jar.put(name, value);
            }
        }
        return jar;
    }

    static String formatCookiePairs(Map<String, String> jar) {
        if (jar == null || jar.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : jar.entrySet()) {
            if (!isSafeCookieToken(e.getKey()) || !isSafeCookieToken(e.getValue())) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * 跳转响应里的 Set-Cookie 写回本次探测的临时 jar。
     * Domain 对不上当前主机的不收，避免把第三方域的 cookie 带到笔记站。
     */
    static void absorbSetCookies(Map<String, String> jar, String requestHost, List<String> setCookies) {
        if (jar == null || setCookies == null) {
            return;
        }
        for (String raw : setCookies) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String first = raw.split(";", 2)[0].trim();
            int eq = first.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = first.substring(0, eq).trim();
            String value = first.substring(eq + 1).trim();
            if (!isSafeCookieToken(name) || name.startsWith("$")) {
                continue;
            }
            String domain = cookieAttribute(raw, "domain");
            if (StringUtils.hasText(domain) && !hostMatchesCookieDomain(requestHost, domain)) {
                continue;
            }
            if (!StringUtils.hasText(value) || "deleted".equalsIgnoreCase(value)) {
                jar.remove(name);
            } else if (isSafeCookieToken(value)) {
                jar.put(name, value);
            }
        }
    }

    static boolean hostMatchesCookieDomain(String host, String domainAttr) {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(domainAttr)) {
            return false;
        }
        String domain = domainAttr.trim().toLowerCase(Locale.ROOT);
        while (domain.startsWith(".")) {
            domain = domain.substring(1);
        }
        if (!StringUtils.hasText(domain)) {
            return false;
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        return hostLower.equals(domain) || hostLower.endsWith("." + domain);
    }

    private static String cookieAttribute(String setCookie, String attr) {
        String[] parts = setCookie.split(";");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            int eq = p.indexOf('=');
            String key = (eq < 0 ? p : p.substring(0, eq)).trim();
            if (attr.equalsIgnoreCase(key)) {
                return eq < 0 ? "" : p.substring(eq + 1).trim();
            }
        }
        return "";
    }

    /** 只拒绝会拆坏 Cookie 头或被 HttpClient 拒掉的字符；空值允许（{@code name=}）。 */
    private static boolean isSafeCookieToken(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 31 || c == 127 || c == ';') {
                return false;
            }
        }
        return true;
    }

    static boolean isBlockedProbeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("0.0.0.0") || h.endsWith(".localhost")
                || h.equals("metadata.google.internal") || h.endsWith(".internal")) {
            return true;
        }
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("169.254.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            String[] seg = h.split("\\.");
            if (seg.length >= 2) {
                try {
                    int second = Integer.parseInt(seg[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
        }
        return false;
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

    /** @return 被停用的链接，便于回复时对上用户刚看到的那一条 */
    public String softDelete(long id, String operatorStaffId, boolean fromGroup) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, url, open_conversation_id, conversation_type, creator_staff_id, status
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
        Object url = row.get("url");
        return url == null ? "" : String.valueOf(url);
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
                SELECT id, url, timeout_ms, open_conversation_id, conversation_type, source,
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
        String probeCookie = resolveProbeCookieForUrl(url);
        boolean withAuthCookie = StringUtils.hasText(probeCookie);
        String hostSuffixes = withAuthCookie ? getProbeCookieConfig().hostSuffixes() : "";

        boolean ok = false;
        Integer status = null;
        String err = "";
        // 只有最终一跳确实带上了 Cookie 才把登录墙当成会话问题；跨站丢 Cookie 不再误判。
        boolean cookieOnFinal = false;
        try {
            FetchedPage page = fetchDocument(url, probeCookie, hostSuffixes, timeout);
            status = page.status();
            cookieOnFinal = page.cookieOnFinal();
            boolean judgeWithCookie = withAuthCookie && cookieOnFinal;
            if (status == 404 || status == 403 || status == 410 || status == 451 || status >= 500) {
                err = "HTTP " + status;
            } else if (status < 200 || status >= 400) {
                err = "HTTP " + status;
            } else {
                // 失效笔记常见：302→首页/404/其它内容，落地仍 HTTP 200，不能只看状态码。
                // 无 Cookie：跳登录页 ≠ 失效；有 Cookie：登录墙可视为会话失效或内容不可见。
                boolean contentDeep = isContentDeepLink(url);
                String soft = detectSoftRedirect(url, page.uri(), judgeWithCookie);
                if (StringUtils.hasText(soft)) {
                    err = soft;
                } else {
                    String body = decodeBody(page.body(), page.contentType());
                    String unavailable = detectUnavailableHint(body, contentDeep, judgeWithCookie);
                    // 仅登录态探测才校验 SSR 笔记载荷：无 Cookie 时登录空壳与失效空壳难区分，避免误杀
                    if (!StringUtils.hasText(unavailable) && contentDeep && judgeWithCookie) {
                        // 同 id 落地仍可能是空壳：失效笔记常不跳转、也无「已删除」明文
                        String contentId = extractContentId(normalizePath(URI.create(url.trim()).getPath()));
                        unavailable = detectMissingNotePayload(body, contentId);
                    }
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
        boolean cookieSessionDead = !ok && withAuthCookie && cookieOnFinal && looksLikeProbeCookieExpired(err);
        jdbc.update("""
                INSERT INTO ops_link_monitor_check_log (monitor_id, checked_at, ok, http_status, latency_ms, error_msg)
                VALUES (?, NOW(), ?, ?, ?, ?)
                """, monitorId, ok ? 1 : 0, status, latency, err);
        if (cookieSessionDead) {
            // 不改 last_ok，避免把「Cookie 挂了」记成「笔记挂了」；其它链接继续探测
            jdbc.update("""
                    UPDATE ops_link_monitor SET last_check_at = NOW(), last_error = ?, update_time = NOW()
                    WHERE id = ?
                    """, err, monitorId);
            boolean alerted = maybeAlertProbeCookieExpired(monitorId, url, err);
            return new ProbeResult(false, err, alerted);
        }
        jdbc.update("""
                UPDATE ops_link_monitor SET last_check_at = NOW(), last_ok = ?, last_status_code = ?,
                  last_error = ?, update_time = NOW()
                WHERE id = ?
                """, ok ? 1 : 0, status, err, monitorId);
        boolean alerted = !ok && alertFailure(monitorId, url, err, row, forceAlert);
        return new ProbeResult(ok, err, alerted);
    }

    /**
     * 跟随重定向并在适用域名上重放 Cookie（含跳转途中的 Set-Cookie）。
     * JDK {@code HttpClient.Redirect.NORMAL} 在主机变化时会丢掉 Cookie，小红书短链会落到登录页。
     */
    private FetchedPage fetchDocument(String url, String cookieHeader, String hostSuffixes, int timeoutMs) throws Exception {
        Map<String, String> jar = parseCookiePairs(cookieHeader);
        URI current = URI.create(url.trim());
        int timeout = Math.max(3000, timeoutMs);
        for (int hop = 0; hop <= MAX_PROBE_REDIRECTS; hop++) {
            if (isBlockedProbeHost(current.getHost())) {
                throw new IllegalStateException("重定向到内网地址，已中止");
            }
            boolean sendCookie = !jar.isEmpty() && hostMatchesCookieSuffix(current.getHost(), hostSuffixes);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(current)
                    .timeout(Duration.ofMillis(timeout))
                    .GET()
                    .header("User-Agent", BROWSER_UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            if (sendCookie) {
                builder.header("Cookie", formatCookiePairs(jar));
            }
            HttpResponse<byte[]> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            absorbSetCookies(jar, current.getHost(), resp.headers().allValues("set-cookie"));
            int code = resp.statusCode();
            String contentType = resp.headers().firstValue("Content-Type").orElse("");
            if (code >= 300 && code < 400 && hop < MAX_PROBE_REDIRECTS) {
                String loc = resp.headers().firstValue("location").orElse("").trim();
                if (!StringUtils.hasText(loc)) {
                    return new FetchedPage(code, current, resp.body(), contentType, sendCookie);
                }
                URI next = current.resolve(loc);
                String scheme = next.getScheme() == null ? "" : next.getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    return new FetchedPage(code, current, resp.body(), contentType, sendCookie);
                }
                current = next;
                continue;
            }
            if (code >= 300 && code < 400) {
                throw new IllegalStateException("重定向次数过多");
            }
            return new FetchedPage(code, current, resp.body(), contentType, sendCookie);
        }
        throw new IllegalStateException("重定向次数过多");
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
                    【链接监控】这条链接带 Cookie 探测仍跳到登录页
                    本次没有把它记成笔记失效，同轮其它链接会继续探测。
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
        // SSR JSON 里常见 \\uXXXX，先还原再检文案
        String rawDecoded = unescapeJsonUnicode(body);
        String text = rawDecoded.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
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
                {"笔记已删除", "笔记已删除"},
                {"内容已删除", "内容已删除"},
                {"该内容无法展示", "该内容无法展示"},
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
                {"笔记已删除", "笔记已删除"},
                {"内容已删除", "内容已删除"},
                {"该内容无法展示", "该内容无法展示"},
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
            if (text.contains(p[0]) || lower.contains(p[0].toLowerCase(Locale.ROOT))
                    || rawDecoded.contains(p[0])) {
                return p[1];
            }
        }
        // 带 Cookie 时只认 title 含登录；正文「立即登录」按钮在已登录页也常见，不当会话失效
        if (contentDeep && withAuthCookie) {
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

    /**
     * 笔记深链：URL 仍带原 id，但 SSR 明确未下发该笔记（失效常见表现）。
     * <p>
     * 注意：小红书 SSR 里同一 noteId 会先出现在路由态，真正笔记载荷在 {@code noteDetailMap}；
     * {@code comments} 也可能很长。旧逻辑对「全文第一次出现 id 后 2500 字」抠 title/desc，
     * 会把仍有效的笔记误判成「笔记内容字段缺失」（生产已实锤）。
     * 只在有「note 为空 / 无 map 且无有效标题」等硬证据时判失效；结构吃不准时放行。
     */
    static String detectMissingNotePayload(String body, String contentId) {
        if (!StringUtils.hasText(body) || !StringUtils.hasText(contentId)) {
            return "";
        }
        String raw = unescapeJsonUnicode(body);
        boolean hasInitial = body.contains("__INITIAL_STATE__") || raw.contains("__INITIAL_STATE__");
        boolean hasNoteMap = body.contains("noteDetailMap") || raw.contains("noteDetailMap");
        boolean specificTitle = hasSpecificNoteHtmlTitle(body);
        if (hasNoteMap) {
            int mapIdx = indexOfNoteDetailMap(raw);
            int idInMap = mapIdx >= 0 ? raw.indexOf("\"" + contentId + "\"", mapIdx) : -1;
            if (idInMap < 0) {
                // map 在，但没有本条 id：若浏览器标题已是具体笔记名，仍视为可读
                return specificTitle ? "" : "页面未包含原笔记数据";
            }
            String mapEntryWindow = raw.substring(idInMap, Math.min(raw.length(), idInMap + 12000));
            int noteKey = mapEntryWindow.indexOf("\"note\"");
            if (noteKey >= 0) {
                String afterNote = mapEntryWindow.substring(noteKey);
                if (Pattern.compile("^\"note\"\\s*:\\s*(null|\\{\\s*\\})").matcher(afterNote).find()) {
                    return specificTitle ? "" : "笔记数据为空或不存在";
                }
                if (looksLikeNoteObject(afterNote)) {
                    return "";
                }
            }
            // map 里有本条 id、结构吃不准（如 comments 很大 / 字段改名）：宁可放行，避免误杀
            return "";
        }
        if (hasInitial) {
            // 登录壳 / 风控空壳常见：有 INITIAL_STATE 但无 noteDetailMap
            // 若 <title> 已是具体笔记名，说明仍可读，不判死
            return specificTitle ? "" : "页面未返回笔记内容";
        }
        if (specificTitle) {
            return "";
        }
        Matcher titleM = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(body);
        if (titleM.find()) {
            String title = titleM.group(1).replaceAll("\\s+", " ").trim();
            if (isGenericXhsTitle(title)) {
                return "页面无有效笔记标题";
            }
        }
        return "";
    }

    /** {@code noteDetailMap} 在 SSR JSON 中的位置；找不到返回 -1。 */
    static int indexOfNoteDetailMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return -1;
        }
        int i = raw.indexOf("\"noteDetailMap\"");
        if (i < 0) {
            i = raw.indexOf("noteDetailMap");
        }
        return i;
    }

    /** 「听劝！… - 小红书」这类具体标题，不是站点壳标题。 */
    static boolean hasSpecificNoteHtmlTitle(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        Matcher titleM = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(body);
        if (!titleM.find()) {
            return false;
        }
        String title = titleM.group(1).replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(title) || isGenericXhsTitle(title)) {
            return false;
        }
        // 小红书笔记页标题常见后缀
        return title.contains("小红书") || title.length() >= 8;
    }

    static boolean isGenericXhsTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return true;
        }
        String t = title.replaceAll("\\s+", " ").trim();
        return "小红书".equals(t)
                || "小红书 - 你的生活兴趣社区".equals(t)
                || "xiaohongshu".equalsIgnoreCase(t);
    }

    /** {@code "note":{...}} 片段里是否像真实笔记载荷（而非 null/空对象）。 */
    static boolean looksLikeNoteObject(String afterNoteKey) {
        if (!StringUtils.hasText(afterNoteKey)) {
            return false;
        }
        Matcher m = Pattern.compile("^\"note\"\\s*:\\s*").matcher(afterNoteKey);
        if (!m.find()) {
            return false;
        }
        String rest = afterNoteKey.substring(m.end()).stripLeading();
        if (rest.startsWith("null") || rest.startsWith("{}")) {
            return false;
        }
        if (!rest.startsWith("{")) {
            return false;
        }
        // 真实笔记常见字段；有其一即可
        return rest.contains("\"noteId\"")
                || rest.contains("\"title\"")
                || rest.contains("\"displayTitle\"")
                || rest.contains("\"desc\"")
                || rest.contains("\"xsecToken\"")
                || rest.contains("\"type\"")
                || rest.contains("\"imageList\"")
                || rest.contains("\"video\"");
    }

    static String unescapeJsonUnicode(String s) {
        if (!StringUtils.hasText(s) || !s.contains("\\u")) {
            return s == null ? "" : s;
        }
        Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start());
            try {
                sb.append((char) Integer.parseInt(m.group(1), 16));
            } catch (Exception ex) {
                sb.append(m.group());
            }
            last = m.end();
        }
        sb.append(s, last, s.length());
        return sb.toString();
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
     *   <li>有绑定群：必须先推该群（@创建人）；群成功则不再打兜底账号</li>
     *   <li>群失败 / 无绑定群：再通知创建人；创建人也失败才兜底 wangfangyang</li>
     *   <li>定时同自然日去重；手动 forceAlert 不过滤当日</li>
     * </ul>
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
        String conversationId = row.get("open_conversation_id") == null ? "" : String.valueOf(row.get("open_conversation_id")).trim();
        String conversationType = row.get("conversation_type") == null ? "1" : String.valueOf(row.get("conversation_type")).trim();
        String source = row.get("source") == null ? "" : String.valueOf(row.get("source")).trim();
        String creatorStaffId = row.get("creator_staff_id") == null ? "" : String.valueOf(row.get("creator_staff_id"));
        Long creatorUserId = row.get("creator_user_id") == null ? null : ((Number) row.get("creator_user_id")).longValue();
        String groupTitle = resolveGroupTitle(conversationId);

        // 群会话：type=2；openConversationId 以 cid 开头也视为群（兼容历史 type 脏数据）；单聊 type=1 不推群
        boolean isGroup = StringUtils.hasText(conversationId)
                && !"1".equals(conversationType)
                && ("2".equals(conversationType)
                || conversationId.regionMatches(true, 0, "cid", 0, 3)
                || "robot".equalsIgnoreCase(source));

        String creatorName = resolveCreatorDisplayName(creatorStaffId, creatorUserId,
                row.get("create_by") == null ? "" : String.valueOf(row.get("create_by")));
        String atUserId = resolveDingUserId(creatorStaffId, creatorUserId);

        StringBuilder contentSb = new StringBuilder();
        contentSb.append("【链接监控告警】链接不可用\n");
        contentSb.append("URL：").append(url).append("\n");
        contentSb.append("原因：").append(err).append("\n");
        contentSb.append("时间：").append(LocalDateTime.now()).append("\n");
        if (isGroup) {
            contentSb.append("来源群：").append(StringUtils.hasText(groupTitle) ? groupTitle : conversationId);
        } else {
            contentSb.append("来源：").append(forceAlert ? "后台手动探测" : ("1".equals(conversationType) ? "单聊/后台" : "未知"));
        }
        if (StringUtils.hasText(creatorName)) {
            contentSb.append("\n创建人：").append(creatorName);
        }
        String content = contentSb.toString();
        boolean alerted = false;

        if (isGroup) {
            List<String> atList = StringUtils.hasText(atUserId) ? List.of(atUserId) : List.of();
            List<String> atNames = StringUtils.hasText(creatorName) ? List.of(creatorName) : List.of();
            DingTalkCalendarClient.NoticeCall groupCall = dingTalkCalendarClient.sendRobotGroupMarkdown(
                    conversationId, "链接监控告警", content.replace("\n", "  \n"), atList, atNames);
            String targetLabel = StringUtils.hasText(groupTitle)
                    ? (groupTitle + "|" + conversationId)
                    : conversationId;
            String groupErr = groupCall.success() ? "" : (groupCall.message() == null ? "群消息失败" : groupCall.message());
            // resumeSent=true 表示 sessionWebhook 真实 @ 成功（客户端显示 @姓名）
            boolean realAt = groupCall.success() && groupCall.resumeSent();
            writeAlert(monitorId, "GROUP", targetLabel, content, groupCall.success(),
                    groupCall.success()
                            ? (realAt ? "群内真实@" : (atList.isEmpty()
                            ? "已推群（无userid，仅明文）"
                            : "已推群（OpenAPI不支持真@，正文@姓名；已尝试工作通知）"))
                            : groupErr);
            if (groupCall.success()) {
                alerted = true;
                // OpenAPI 无法真 @：再给创建人发工作通知，确保有人被点到
                if (!realAt && StringUtils.hasText(atUserId)) {
                    notifyCreatorOrFallback(monitorId, content, creatorStaffId, creatorUserId);
                }
            } else {
                log.warn("link monitor group alert failed monitorId={} cid={} err={}", monitorId, conversationId, groupErr);
                notifyByUsername(monitorId, content + "\n（兜底：群告警失败：" + groupErr + "）",
                        RobotProductRules.DM_ALERT_USERNAME);
                alerted = true;
            }
        } else {
            writeAlert(monitorId, "ADMIN", forceAlert ? "backend-manual" : "backend", content, true,
                    StringUtils.hasText(conversationId) ? "" : "无绑定告警群");
            // 无群：通知创建人，失败才兜底
            alerted = notifyCreatorOrFallback(monitorId, content, creatorStaffId, creatorUserId);
            if (!alerted) {
                notifyByUsername(monitorId, content + "\n（兜底：监控未绑定告警群且创建人通知失败）",
                        RobotProductRules.DM_ALERT_USERNAME);
                alerted = true;
            }
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

    /** @return 创建人单聊是否发送成功（不含兜底） */
    private boolean notifyCreatorOrFallback(long monitorId, String content, String creatorStaffId, Long creatorUserId) {
        String dingUserId = resolveDingUserId(creatorStaffId, creatorUserId);
        String targetLabel = StringUtils.hasText(creatorStaffId) ? creatorStaffId : ("uid:" + creatorUserId);
        if (!StringUtils.hasText(dingUserId)) {
            return false;
        }
        try {
            DingTalkCalendarClient.NoticeCall call = dingTalkCalendarClient.notifyMarkdown(
                    dingUserId, "链接监控告警", content);
            writeAlert(monitorId, "USER", targetLabel, content, call.success(),
                    call.success() ? "" : call.message());
            return call.success();
        } catch (Exception ex) {
            writeAlert(monitorId, "USER", targetLabel, content, false, ex.getMessage());
            return false;
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
