package com.base.admin.robot;

import com.base.admin.exception.BusinessException;
import com.base.admin.service.DingTalkCalendarClient;
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
    private final JdbcTemplate jdbc;
    private final DingTalkCalendarClient dingTalkCalendarClient;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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
            ps.setString(10, createBy == null ? "" : createBy);
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
                """, Math.max(1, Math.min(limit, 100)));
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
                """, Math.max(1, Math.min(limit, 100)));
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
                       creator_staff_id, creator_user_id
                FROM ops_link_monitor WHERE id = ? AND is_active = 1 LIMIT 1
                """, monitorId);
        if (rows.isEmpty()) {
            return new ProbeResult(false, "监控不存在", false);
        }
        Map<String, Object> row = rows.getFirst();
        String url = String.valueOf(row.get("url"));
        int timeout = row.get("timeout_ms") == null ? 15000 : ((Number) row.get("timeout_ms")).intValue();
        long start = System.currentTimeMillis();
        boolean ok = false;
        Integer status = null;
        String err = "";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(3000, timeout)))
                    .GET()
                    .header("User-Agent", BROWSER_UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            status = resp.statusCode();
            if (status == 404 || status == 403 || status == 410 || status == 451 || status >= 500) {
                err = "HTTP " + status;
            } else if (status < 200 || status >= 400) {
                err = "HTTP " + status;
            } else {
                // 小红书等：失效笔记 302→/404 后仍返回 HTTP 200，需比对最终落地 URL
                String soft = detectSoftRedirect(url, resp.uri());
                if (StringUtils.hasText(soft)) {
                    err = soft;
                } else {
                    String body = decodeBody(resp.body(), resp.headers().firstValue("Content-Type").orElse(""));
                    String unavailable = detectUnavailableHint(body);
                    if (StringUtils.hasText(unavailable)) {
                        err = "页面提示不可用：" + unavailable;
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
            alerted = alertFailure(monitorId, url, err, row, forceAlert);
        }
        return new ProbeResult(ok, err, alerted);
    }

    /**
     * 跟随重定向后的落地页相对原链接「跑偏」：如小红书失效笔记 → /404（仍 HTTP 200）。
     */
    static String detectSoftRedirect(String originalUrl, URI finalUri) {
        if (finalUri == null || !StringUtils.hasText(originalUrl)) {
            return "";
        }
        String path = finalUri.getPath() == null ? "" : finalUri.getPath();
        String pathLower = path.toLowerCase(Locale.ROOT);
        String queryDecoded = "";
        if (StringUtils.hasText(finalUri.getRawQuery())) {
            try {
                queryDecoded = URLDecoder.decode(finalUri.getRawQuery(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                queryDecoded = finalUri.getRawQuery();
            }
        }
        if (pathLower.equals("/404") || pathLower.startsWith("/404/")) {
            String hint = extractQueryErrorMsg(queryDecoded);
            if (StringUtils.hasText(hint)) {
                return "链接已失效（" + hint + "）";
            }
            return "链接已失效（已跳转到404页）";
        }
        try {
            URI orig = URI.create(originalUrl.trim());
            String origPath = orig.getPath() == null ? "" : orig.getPath();
            String origLower = origPath.toLowerCase(Locale.ROOT);
            boolean contentDeep = origLower.contains("/discovery/item/")
                    || origLower.contains("/explore/")
                    || origLower.matches(".*/(item|note|detail|post|status|aweme|video)/[^/]+.*");
            String finalNorm = path.isEmpty() ? "/" : path;
            if (contentDeep && (finalNorm.equals("/") || finalNorm.equalsIgnoreCase("/explore")
                    || finalNorm.equalsIgnoreCase("/index") || finalNorm.equalsIgnoreCase("/home")
                    || finalNorm.equalsIgnoreCase("/feed"))) {
                return "链接已失效（已重定向到首页）";
            }
        } catch (Exception ignored) {
            // ignore parse errors
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
     * 识别页面正文中的「不可用」提示：404 / 无权限 / 已删除 / 文章不存在 等。
     * HTTP 200 但正文含这些文案也判失败。
     */
    static String detectUnavailableHint(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String text = body.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ");
        String lower = text.toLowerCase(Locale.ROOT);
        String[][] patterns = {
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
        String content = contentSb.toString();
        String atUserId = resolveDingUserId(creatorStaffId, creatorUserId);
        boolean alerted = false;

        if (forceAlert) {
            // 手动探测：群 + @创建人；否则兜底 wangfangyang
            if (isGroup) {
                List<String> atList = StringUtils.hasText(atUserId) ? List.of(atUserId) : List.of();
                DingTalkCalendarClient.NoticeCall groupCall = dingTalkCalendarClient.sendRobotGroupMarkdown(
                        conversationId, "链接监控告警", content.replace("\n", "  \n"), atList);
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
            DingTalkCalendarClient.NoticeCall groupCall = dingTalkCalendarClient.sendRobotGroupMarkdown(
                    conversationId, "链接监控告警", content.replace("\n", "  \n"),
                    StringUtils.hasText(atUserId) ? List.of(atUserId) : List.of());
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

    private String resolveDingUserId(String staffId, Long userId) {
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
            // staffId 本身就是钉钉 userid，可直接用于工作通知
            return staffId.trim();
        }
        if (userId != null) {
            return jdbc.query("""
                    SELECT d.dingtalk_user_id
                    FROM hr_user_dingtalk d
                    INNER JOIN sys_user u ON u.user_id = d.user_id AND u.is_active = 1
                    WHERE d.is_active = 1 AND d.user_id = ?
                    LIMIT 1
                    """, rs -> rs.next() ? rs.getString(1) : null, userId);
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
