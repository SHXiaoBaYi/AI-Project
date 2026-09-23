package com.base.admin.service;

import com.base.admin.config.DingTalkProperties;
import com.base.admin.domain.vo.DingTalkDirectoryUserVO;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DingTalkCalendarClient {

    private static final DateTimeFormatter DING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DingTalkProperties properties;
    private final DingTalkAppService dingTalkAppService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public CalendarCall createEvent(String unionId, String title, String description, LocalDateTime start,
                                    int durationMin, String location) {
        return createEvent(unionId, title, description, start, durationMin, location, List.of(), false);
    }

    /**
     * 在 owner 的主日历建日程；attendeeUnionIds 为额外参与人 unionId（不含 owner 也可再传）。
     * onlineMeeting=true 时写入钉钉日程扩展字段，尽量拉起线上会议能力。
     */
    public CalendarCall createEvent(String ownerUnionId, String title, String description, LocalDateTime start,
                                    int durationMin, String location, List<String> attendeeUnionIds, boolean onlineMeeting) {
        if (!ready()) {
            return CalendarCall.fail("钉钉未配置。请到「系统管理 → 钉钉应用配置」填写并启用");
        }
        if (!StringUtils.hasText(ownerUnionId)) {
            return CalendarCall.fail("没有钉钉 unionId，不能建日程");
        }
        try {
            String token = accessToken();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("summary", title);
            if (StringUtils.hasText(description)) {
                body.put("description", description);
            }
            body.put("isAllDay", false);
            body.set("start", timeNode(start));
            body.set("end", timeNode(start.plusMinutes(Math.max(durationMin, 15))));
            if (StringUtils.hasText(location)) {
                ObjectNode place = objectMapper.createObjectNode();
                place.put("displayName", location.trim());
                body.set("location", place);
            }
            ArrayNode attendees = body.putArray("attendees");
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
            ids.add(ownerUnionId.trim());
            if (attendeeUnionIds != null) {
                for (String id : attendeeUnionIds) {
                    if (StringUtils.hasText(id)) {
                        ids.add(id.trim());
                    }
                }
            }
            for (String id : ids) {
                ObjectNode attendee = objectMapper.createObjectNode();
                attendee.put("id", id);
                attendees.add(attendee);
            }
            if (onlineMeeting) {
                ObjectNode extra = objectMapper.createObjectNode();
                extra.put("onlineMeetingOpen", true);
                body.set("extra", extra);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.dingtalk.com/v1.0/calendar/users/" + encode(ownerUnionId) + "/calendars/primary/events"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("x-acs-dingtalk-access-token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return CalendarCall.fail("钉钉创建日程失败：" + response.body(), response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            String eventId = json.path("id").asText("");
            if (!StringUtils.hasText(eventId)) {
                return CalendarCall.fail("钉钉未返回日程 ID", response.body());
            }
            return CalendarCall.ok(eventId, response.body());
        } catch (Exception ex) {
            return CalendarCall.fail("钉钉创建日程失败：" + ex.getMessage());
        }
    }

    /**
     * 钉钉日程接口本身不支持附件。创建日程后改为：工作通知发流程说明 + 把简历当文件消息发给面试官。
     */
    public NoticeCall notifyInterview(String dingUserId, String markdownTitle, String markdownText,
                                      Path resumeFile, String resumeFileName) {
        if (!ready()) {
            return NoticeCall.fail("钉钉未配置");
        }
        if (!StringUtils.hasText(dingUserId)) {
            return NoticeCall.fail("面试官没有钉钉 userid，不能发工作通知");
        }
        Long agentId = dingTalkAppService.agentId();
        if (agentId == null) {
            return NoticeCall.fail("钉钉应用未配置 AgentId，不能发工作通知");
        }
        try {
            String token = legacyToken(credential());
            sendWorkNotice(token, agentId, dingUserId, markdownMsg(markdownTitle, markdownText));
            boolean resumeSent = false;
            if (resumeFile != null && Files.isRegularFile(resumeFile)) {
                String mediaId = uploadMedia(token, resumeFile, resumeFileName);
                sendWorkNotice(token, agentId, dingUserId, fileMsg(mediaId));
                resumeSent = true;
            }
            return NoticeCall.ok(resumeSent);
        } catch (BusinessException ex) {
            return NoticeCall.fail(ex.getMessage());
        } catch (Exception ex) {
            return NoticeCall.fail("发送钉钉工作通知失败：" + ex.getMessage());
        }
    }

    /** 纯 Markdown 工作通知（无附件） */
    public NoticeCall notifyMarkdown(String dingUserId, String markdownTitle, String markdownText) {
        return notifyInterview(dingUserId, markdownTitle, markdownText, null, null);
    }

    public CalendarCall deleteEvent(String unionId, String eventId) {
        if (!ready()) {
            return CalendarCall.fail("钉钉未配置。请到「系统管理 → 钉钉应用配置」填写并启用");
        }
        try {
            String token = accessToken();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.dingtalk.com/v1.0/calendar/users/" + encode(unionId)
                            + "/calendars/primary/events/" + encode(eventId) + "?pushNotification=true"))
                    .timeout(Duration.ofSeconds(15))
                    .header("x-acs-dingtalk-access-token", token)
                    .DELETE()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return CalendarCall.fail("钉钉取消日程失败：" + response.body(), response.body());
            }
            return CalendarCall.ok(eventId, response.body().isBlank() ? "{\"deleted\":true}" : response.body());
        } catch (Exception ex) {
            return CalendarCall.fail("钉钉取消日程失败：" + ex.getMessage());
        }
    }

    /** 更新主日历事件（主题/时间/地点/描述/参与人） */
    public CalendarCall updateEvent(String ownerUnionId, String eventId, String title, String description,
                                    LocalDateTime start, int durationMin, String location,
                                    List<String> attendeeUnionIds, boolean onlineMeeting) {
        if (!ready()) {
            return CalendarCall.fail("钉钉未配置。请到「系统管理 → 钉钉应用配置」填写并启用");
        }
        if (!StringUtils.hasText(ownerUnionId) || !StringUtils.hasText(eventId)) {
            return CalendarCall.fail("缺少 unionId 或日程 ID，不能更新");
        }
        try {
            String token = accessToken();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("id", eventId);
            body.put("summary", title);
            if (StringUtils.hasText(description)) {
                body.put("description", description);
            } else {
                body.put("description", "");
            }
            body.put("isAllDay", false);
            body.set("start", timeNode(start));
            body.set("end", timeNode(start.plusMinutes(Math.max(durationMin, 15))));
            ObjectNode place = objectMapper.createObjectNode();
            place.put("displayName", StringUtils.hasText(location) ? location.trim() : "");
            body.set("location", place);
            ArrayNode attendees = body.putArray("attendees");
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
            ids.add(ownerUnionId.trim());
            if (attendeeUnionIds != null) {
                for (String id : attendeeUnionIds) {
                    if (StringUtils.hasText(id)) {
                        ids.add(id.trim());
                    }
                }
            }
            for (String id : ids) {
                ObjectNode attendee = objectMapper.createObjectNode();
                attendee.put("id", id);
                attendees.add(attendee);
            }
            ObjectNode extra = objectMapper.createObjectNode();
            extra.put("onlineMeetingOpen", onlineMeeting);
            body.set("extra", extra);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.dingtalk.com/v1.0/calendar/users/" + encode(ownerUnionId)
                            + "/calendars/primary/events/" + encode(eventId)))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("x-acs-dingtalk-access-token", token)
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return CalendarCall.fail("钉钉更新日程失败：" + response.body(), response.body());
            }
            return CalendarCall.ok(eventId, response.body().isBlank() ? "{\"updated\":true}" : response.body());
        } catch (Exception ex) {
            return CalendarCall.fail("钉钉更新日程失败：" + ex.getMessage());
        }
    }

    public boolean configured() {
        return ready();
    }

    public DingIdentity resolveByMobile(String mobile, String nickname) {
        DingTalkAppService.Credential credential = credential();
        String normalized = normalizeMobile(mobile);
        if (credential == null || !StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            String token = legacyToken(credential);
            String userId = useridFromLegacyMobile(token, normalized);
            if (!StringUtils.hasText(userId)) {
                userId = useridFromV2Mobile(token, normalized);
            }
            if (StringUtils.hasText(userId)) {
                return identityOf(token, userId);
            }
            List<DingTalkDirectoryUserVO> directory = listDirectory();
            for (DingTalkDirectoryUserVO row : directory) {
                if (!normalized.equals(normalizeMobile(row.getMobile()))) {
                    continue;
                }
                if (StringUtils.hasText(row.getUserid()) && StringUtils.hasText(row.getUnionId())) {
                    return new DingIdentity(row.getUserid(), row.getUnionId());
                }
                if (StringUtils.hasText(row.getUserid())) {
                    return identityOf(token, row.getUserid());
                }
            }
            if (StringUtils.hasText(nickname)) {
                String expected = nickname.trim();
                DingTalkDirectoryUserVO named = null;
                int count = 0;
                for (DingTalkDirectoryUserVO row : directory) {
                    if (!expected.equals(row.getName() == null ? "" : row.getName().trim())) {
                        continue;
                    }
                    count++;
                    named = row;
                }
                if (count == 1 && named != null && StringUtils.hasText(named.getMobile())
                        && !normalized.equals(normalizeMobile(named.getMobile()))) {
                    throw new BusinessException("用户资料手机号是「" + normalized + "」，钉钉通讯录里「" + expected
                            + "」的手机号是「" + named.getMobile() + "」。这两个号码不一致");
                }
            }
            throw new BusinessException("系统用户手机号「" + normalized + "」在已读到的钉钉通讯录里没有对应成员");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("钉钉查询失败：" + ex.getMessage());
        }
    }

    /** 应用权限范围内能读到的已加入成员。同一个人在多个部门只保留一条。 */
    public List<DingTalkDirectoryUserVO> listDirectory() {
        DingTalkAppService.Credential credential = credential();
        if (credential == null) {
            throw new BusinessException("钉钉未配置。请到「系统管理 → 钉钉应用配置」填写并启用");
        }
        try {
            String token = legacyToken(credential);
            Map<String, DingTalkDirectoryUserVO> people = new LinkedHashMap<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(1L);
            Set<Long> seen = new HashSet<>();
            boolean listed = false;
            while (!queue.isEmpty() && seen.size() < 300 && people.size() < 5000) {
                long deptId = queue.removeFirst();
                if (!seen.add(deptId)) {
                    continue;
                }
                long cursor = 0;
                for (int page = 0; page < 50; page++) {
                    ObjectNode body = objectMapper.createObjectNode();
                    body.put("dept_id", deptId);
                    body.put("cursor", cursor);
                    body.put("size", 100);
                    body.put("contain_access_limit", true);
                    JsonNode json = postJson("https://oapi.dingtalk.com/topapi/v2/user/list?access_token=" + encode(token), body);
                    int code = dingCode(json);
                    if (code != 0) {
                        if (!listed && seen.size() == 1) {
                            throw new BusinessException("读取钉钉通讯录失败：" + json.path("errmsg").asText(""));
                        }
                        break;
                    }
                    listed = true;
                    JsonNode list = json.path("result").path("list");
                    if (list.isArray()) {
                        for (JsonNode user : list) {
                            String userId = user.path("userid").asText("");
                            if (!StringUtils.hasText(userId) || people.containsKey(userId)) {
                                continue;
                            }
                            DingTalkDirectoryUserVO row = new DingTalkDirectoryUserVO();
                            row.setName(user.path("name").asText(""));
                            row.setMobile(user.path("mobile").asText(""));
                            row.setStateCode(user.path("state_code").asText(""));
                            row.setTelephone(user.path("telephone").asText(""));
                            row.setExclusiveAccount(user.path("exclusive_account").asBoolean(false));
                            row.setHideMobile(user.path("hide_mobile").asBoolean(false));
                            row.setActive(user.path("active").asBoolean(false));
                            row.setUserid(userId);
                            row.setUnionId(user.path("unionid").asText(""));
                            people.put(userId, row);
                        }
                    }
                    if (!json.path("result").path("has_more").asBoolean(false)) {
                        break;
                    }
                    long next = json.path("result").path("next_cursor").asLong(-1);
                    if (next < 0) {
                        break;
                    }
                    cursor = next;
                }
                ObjectNode sub = objectMapper.createObjectNode();
                sub.put("dept_id", deptId);
                JsonNode subs = postJson("https://oapi.dingtalk.com/topapi/v2/department/listsub?access_token=" + encode(token), sub);
                if (dingCode(subs) == 0 && subs.path("result").isArray()) {
                    for (JsonNode dept : subs.path("result")) {
                        long child = dept.path("dept_id").asLong(0);
                        if (child > 0) {
                            queue.add(child);
                        }
                    }
                }
            }
            List<DingTalkDirectoryUserVO> rows = new ArrayList<>(people.values());
            rows.sort(Comparator.comparing(DingTalkDirectoryUserVO::getName, Comparator.nullsLast(String::compareTo)));
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("读取钉钉通讯录失败：" + ex.getMessage());
        }
    }

    public void testConnection(String clientId, String clientSecret) {
        try {
            accessToken(new DingTalkAppService.Credential(clientId, clientSecret));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("连接钉钉失败：" + ex.getMessage());
        }
    }

    /** 用该用户自己的 unionId 查询其在时间范围内的忙闲片段。钉钉只返回忙和暂定，空闲由调用方补齐。 */
    public BusySchedule queryBusy(String unionId, LocalDateTime start, LocalDateTime end) {
        if (!StringUtils.hasText(unionId)) {
            return new BusySchedule(null, "没有钉钉 unionId", List.of());
        }
        try {
            String token = accessToken();
            ObjectNode body = objectMapper.createObjectNode();
            body.putArray("userIds").add(unionId);
            body.put("startTime", start.format(DING_TIME) + "+08:00");
            body.put("endTime", end.format(DING_TIME) + "+08:00");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.dingtalk.com/v1.0/calendar/users/" + encode(unionId) + "/querySchedule"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("x-acs-dingtalk-access-token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() >= 300) {
                return new BusySchedule(unionId, "查询闲忙失败：" + briefDingError(json, response.body()), List.of());
            }
            JsonNode info = json.path("scheduleInformation");
            if (!info.isArray() || info.isEmpty()) {
                return new BusySchedule(unionId, null, List.of());
            }
            JsonNode mine = null;
            for (JsonNode node : info) {
                if (unionId.equals(node.path("userId").asText(""))) {
                    mine = node;
                    break;
                }
            }
            if (mine == null) {
                mine = info.get(0);
            }
            String error = mine.path("error").asText("");
            List<BusyItem> items = new ArrayList<>();
            for (JsonNode item : mine.path("scheduleItems")) {
                LocalDateTime itemStart = parseDingTime(item.path("start"), false);
                LocalDateTime itemEnd = parseDingTime(item.path("end"), true);
                if (itemStart == null || itemEnd == null || !itemStart.isBefore(itemEnd)) {
                    continue;
                }
                items.add(new BusyItem(item.path("status").asText("BUSY"), itemStart, itemEnd));
            }
            return new BusySchedule(unionId, StringUtils.hasText(error) ? error : null, items);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            return new BusySchedule(unionId, "查询闲忙失败：" + ex.getMessage(), List.of());
        }
    }

    private static LocalDateTime parseDingTime(JsonNode node, boolean end) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String dateTime = node.path("dateTime").asText("");
        if (StringUtils.hasText(dateTime)) {
            return OffsetDateTime.parse(dateTime).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        }
        String date = node.path("date").asText("");
        if (!StringUtils.hasText(date)) {
            return null;
        }
        LocalDate day = LocalDate.parse(date);
        // 全天事件：开始为当天 00:00，结束为次日 00:00，才能盖住整日工作时段
        return end ? day.plusDays(1).atStartOfDay() : day.atStartOfDay();
    }

    private boolean ready() {
        return credential() != null;
    }

    private DingTalkAppService.Credential credential() {
        DingTalkAppService.Credential stored = dingTalkAppService.credential();
        if (stored != null) {
            return stored;
        }
        if (properties.isEnabled()
                && StringUtils.hasText(properties.getClientId())
                && StringUtils.hasText(properties.getClientSecret())) {
            return new DingTalkAppService.Credential(properties.getClientId().trim(), properties.getClientSecret().trim());
        }
        return null;
    }

    private String accessToken() throws Exception {
        return accessToken(credential());
    }

    private String accessToken(DingTalkAppService.Credential credential) throws Exception {
        if (credential == null) {
            throw new BusinessException("钉钉未配置。请到「系统管理 → 钉钉应用配置」填写并启用");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("appKey", credential.clientId());
        body.put("appSecret", credential.clientSecret());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        String token = json.path("accessToken").asText("");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("获取钉钉访问凭证失败：" + briefDingError(json, response.body()));
        }
        return token;
    }

    private String legacyToken(DingTalkAppService.Credential credential) throws Exception {
        String url = "https://oapi.dingtalk.com/gettoken?appkey=" + encode(credential.clientId())
                + "&appsecret=" + encode(credential.clientSecret());
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        String token = json.path("access_token").asText("");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("获取钉钉访问凭证失败：" + briefDingError(json, response.body()));
        }
        return token;
    }

    /** 钉钉通讯录手机号：去掉空格、横线、+86，只留 11 位大陆号码。 */
    static String normalizeMobile(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private String useridFromLegacyMobile(String token, String mobile) throws Exception {
        JsonNode byMobile = getJson("https://oapi.dingtalk.com/user/get_by_mobile?access_token="
                + encode(token) + "&mobile=" + encode(mobile));
        if (dingCode(byMobile) != 0) {
            return "";
        }
        return byMobile.path("userid").asText("");
    }

    private String useridFromV2Mobile(String token, String mobile) throws Exception {
        JsonNode byMobile = postForm(
                "https://oapi.dingtalk.com/topapi/v2/user/getbymobile?access_token=" + encode(token),
                "mobile", mobile,
                "support_exclusive_account_search", "true");
        if (dingCode(byMobile) != 0) {
            return "";
        }
        JsonNode result = byMobile.path("result");
        String userId = result.path("userid").asText("");
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        JsonNode exclusive = result.path("exclusive_account_userid_list");
        if (exclusive.isArray()) {
            for (JsonNode item : exclusive) {
                if (StringUtils.hasText(item.asText(""))) {
                    return item.asText("");
                }
            }
        }
        String raw = exclusive.asText("").trim();
        if (raw.startsWith("[")) {
            JsonNode parsed = objectMapper.readTree(raw);
            if (parsed.isArray()) {
                for (JsonNode item : parsed) {
                    if (StringUtils.hasText(item.asText(""))) {
                        return item.asText("");
                    }
                }
            }
        }
        return "";
    }

    private DingIdentity identityOf(String token, String userId) throws Exception {
        ObjectNode get = objectMapper.createObjectNode();
        get.put("userid", userId);
        JsonNode detail = postJson("https://oapi.dingtalk.com/topapi/v2/user/get?access_token=" + encode(token), get);
        assertDingOk(detail, "查询钉钉用户详情失败");
        String unionId = detail.path("result").path("unionid").asText("");
        return StringUtils.hasText(unionId) ? new DingIdentity(userId, unionId) : null;
    }

    /** 遍历部门成员，用通讯录里的手机号匹配被邀请加入的个人账号。 */
    private DingIdentity findInvitedMember(String token, String mobile) throws Exception {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(1L);
        Set<Long> seen = new HashSet<>();
        boolean sawMobile = false;
        int scanned = 0;
        while (!queue.isEmpty() && scanned < 5000 && seen.size() < 300) {
            long deptId = queue.removeFirst();
            if (!seen.add(deptId)) {
                continue;
            }
            long cursor = 0;
            for (int page = 0; page < 50; page++) {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("dept_id", deptId);
                body.put("cursor", cursor);
                body.put("size", 100);
                body.put("contain_access_limit", true);
                JsonNode json = postJson("https://oapi.dingtalk.com/topapi/v2/user/list?access_token=" + encode(token), body);
                int code = dingCode(json);
                if (code != 0) {
                    if (scanned == 0 && seen.size() == 1) {
                        throw new BusinessException("读取钉钉通讯录失败：" + json.path("errmsg").asText(""));
                    }
                    break;
                }
                JsonNode list = json.path("result").path("list");
                if (list.isArray()) {
                    for (JsonNode user : list) {
                        scanned++;
                        String theirMobile = normalizeMobile(user.path("mobile").asText(""));
                        if (StringUtils.hasText(theirMobile)) {
                            sawMobile = true;
                        }
                        if (!mobile.equals(theirMobile)) {
                            continue;
                        }
                        String userId = user.path("userid").asText("");
                        String unionId = user.path("unionid").asText("");
                        if (StringUtils.hasText(userId) && StringUtils.hasText(unionId)) {
                            return new DingIdentity(userId, unionId);
                        }
                        if (StringUtils.hasText(userId)) {
                            return identityOf(token, userId);
                        }
                    }
                }
                if (!json.path("result").path("has_more").asBoolean(false)) {
                    break;
                }
                cursor = json.path("result").path("next_cursor").asLong(-1);
                if (cursor < 0) {
                    break;
                }
            }
            ObjectNode sub = objectMapper.createObjectNode();
            sub.put("dept_id", deptId);
            JsonNode subs = postJson("https://oapi.dingtalk.com/topapi/v2/department/listsub?access_token=" + encode(token), sub);
            if (dingCode(subs) == 0 && subs.path("result").isArray()) {
                for (JsonNode dept : subs.path("result")) {
                    long child = dept.path("dept_id").asLong(0);
                    if (child > 0) {
                        queue.add(child);
                    }
                }
            }
        }
        if (scanned > 0 && !sawMobile) {
            throw new BusinessException("通讯录成员已读到，但钉钉没有返回手机号。请给应用开通「企业员工手机号信息」权限。自己注册、被企业邀请的成员无法只靠按号码查询接口");
        }
        return null;
    }

    private ObjectNode markdownMsg(String title, String text) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("msgtype", "markdown");
        ObjectNode markdown = objectMapper.createObjectNode();
        markdown.put("title", title);
        markdown.put("text", text);
        msg.set("markdown", markdown);
        return msg;
    }

    private ObjectNode fileMsg(String mediaId) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("msgtype", "file");
        ObjectNode file = objectMapper.createObjectNode();
        file.put("media_id", mediaId);
        msg.set("file", file);
        return msg;
    }

    private void sendWorkNotice(String token, Long agentId, String dingUserId, ObjectNode msg) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("agent_id", agentId);
        body.put("userid_list", dingUserId);
        body.put("to_all_user", false);
        body.set("msg", msg);
        JsonNode json = postJson(
                "https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2?access_token=" + encode(token),
                body);
        assertDingOk(json, "发送钉钉工作通知失败");
    }

    private String uploadMedia(String token, Path file, String fileName) throws Exception {
        if (Files.size(file) > 20L * 1024 * 1024) {
            throw new BusinessException("简历超过 20MB，钉钉不能作为工作通知附件发送");
        }
        String name = StringUtils.hasText(fileName) ? fileName : file.getFileName().toString();
        String boundary = "----DingTalk" + UUID.randomUUID().toString().replace("-", "");
        byte[] fileBytes = Files.readAllBytes(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String preamble = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"media\"; filename=\"" + name.replace("\"", "") + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        out.write(preamble.getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oapi.dingtalk.com/media/upload?access_token=" + encode(token) + "&type=file"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        assertDingOk(json, "上传简历到钉钉失败");
        String mediaId = json.path("media_id").asText("");
        if (!StringUtils.hasText(mediaId)) {
            throw new BusinessException("钉钉未返回简历 media_id");
        }
        return mediaId;
    }

    private JsonNode postForm(String url, String... pairs) throws Exception {
        StringBuilder form = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (!form.isEmpty()) {
                form.append('&');
            }
            form.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8));
            form.append('=');
            form.append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private JsonNode postJson(String url, ObjectNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private static int dingCode(JsonNode json) {
        JsonNode code = json.path("errcode");
        if (code.isNumber()) {
            return code.asInt();
        }
        if (code.isTextual()) {
            try {
                return Integer.parseInt(code.asText("").trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static void assertDingOk(JsonNode json, String fallback) {
        int code = dingCode(json);
        if (code == 0) {
            return;
        }
        String message = json.path("errmsg").asText("");
        throw new BusinessException(fallback + (StringUtils.hasText(message) ? "：" + message : ""));
    }

    private static String briefDingError(JsonNode json, String raw) {
        String message = json.path("message").asText("");
        if (!StringUtils.hasText(message)) {
            message = json.path("errmsg").asText("");
        }
        if (!StringUtils.hasText(message)) {
            message = raw == null ? "" : raw;
        }
        message = message.replaceAll("(?i)(appsecret|clientsecret|appSecret)\"?\\s*[:=]\\s*\"?[A-Za-z0-9_\\-]{8,}", "$1=***");
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private ObjectNode timeNode(LocalDateTime time) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("dateTime", time.format(DING_TIME) + "+08:00");
        node.put("timeZone", "Asia/Shanghai");
        return node;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record CalendarCall(boolean success, String eventId, String message, String response) {
        static CalendarCall ok(String eventId, String response) {
            return new CalendarCall(true, eventId, null, response);
        }

        static CalendarCall fail(String message) {
            return new CalendarCall(false, null, message, null);
        }

        static CalendarCall fail(String message, String response) {
            return new CalendarCall(false, null, message, response);
        }
    }

    public record NoticeCall(boolean success, boolean resumeSent, String message) {
        static NoticeCall ok(boolean resumeSent) {
            return new NoticeCall(true, resumeSent, null);
        }

        static NoticeCall fail(String message) {
            return new NoticeCall(false, false, message);
        }
    }

    public record DingIdentity(String userId, String unionId) {
    }

    public record BusyItem(String status, LocalDateTime start, LocalDateTime end) {
    }

    public record BusySchedule(String unionId, String error, List<BusyItem> items) {
    }
}
