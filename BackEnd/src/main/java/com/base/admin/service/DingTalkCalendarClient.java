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
import java.security.MessageDigest;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    /**
     * 将本地简历上传到组织人钉盘（企业空间根目录），返回 fileId 与可打开链接。
     * 日程 API 无附件字段时，把 openUrl / fileId 写进日程描述。
     */
    public DriveFile uploadResumeToDrive(String ownerUnionId, Path file, String fileName) {
        if (!ready()) {
            throw new BusinessException("钉钉未配置。请到「系统管理 → 钉钉应用配置」填写并启用");
        }
        if (!StringUtils.hasText(ownerUnionId)) {
            throw new BusinessException("没有钉钉 unionId，不能上传简历到钉盘");
        }
        if (file == null || !Files.isRegularFile(file)) {
            throw new BusinessException("候选人简历文件不可用，无法上传到钉盘");
        }
        try {
            long size = Files.size(file);
            if (size <= 0) {
                throw new BusinessException("简历文件为空，无法上传到钉盘");
            }
            if (size > 100L * 1024 * 1024) {
                throw new BusinessException("简历超过 100MB，钉盘上传失败");
            }
            String token = accessToken();
            String name = sanitizeDriveFileName(fileName, file);
            String md5 = md5Hex(file);
            String spaceId = resolveOrgSpaceId(token, ownerUnionId.trim());
            JsonNode uploadInfo = getDriveUploadInfo(token, spaceId, ownerUnionId.trim(), name, size, md5);
            String mediaId = putDriveFileBytes(token, spaceId, ownerUnionId.trim(), name, size, md5, file, uploadInfo);
            if (!StringUtils.hasText(mediaId)) {
                throw new BusinessException("钉盘上传未返回 mediaId");
            }
            JsonNode added = addDriveFile(token, spaceId, ownerUnionId.trim(), name, mediaId);
            String fileId = added.path("fileId").asText("");
            String savedName = added.path("fileName").asText(name);
            if (!StringUtils.hasText(fileId)) {
                throw new BusinessException("钉盘未返回 fileId：" + cut(added.toString(), 180));
            }
            String openUrl = buildDriveOpenUrl(spaceId, fileId, savedName);
            return new DriveFile(spaceId, fileId, savedName, openUrl);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("上传简历到钉盘失败：" + ex.getMessage());
        }
    }

    /**
     * 给面试官/抄送人授予钉盘文件「可查看/下载」权限（best-effort，失败不抛）。
     * memberId 需为钉钉 staffId（userid）。
     */
    public void grantDriveFileViewer(String ownerUnionId, DriveFile driveFile, List<String> staffIds) {
        if (driveFile == null || !StringUtils.hasText(ownerUnionId) || staffIds == null || staffIds.isEmpty()) {
            return;
        }
        String corpId = dingTalkAppService.corpId();
        if (!StringUtils.hasText(corpId)) {
            return;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String id : staffIds) {
            if (StringUtils.hasText(id)) {
                ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        try {
            String token = accessToken();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("role", "viewer");
            body.put("unionId", ownerUnionId.trim());
            ArrayNode members = body.putArray("members");
            for (String staffId : ids) {
                ObjectNode m = members.addObject();
                m.put("corpId", corpId);
                m.put("memberType", "user");
                m.put("memberId", staffId);
            }
            apiPost(token, "https://api.dingtalk.com/v1.0/drive/spaces/" + encode(driveFile.spaceId())
                    + "/files/" + encode(driveFile.fileId()) + "/permissions", body);
        } catch (Exception ignored) {
            // 日程描述仍保留 fileId 链接；授权失败不阻断建日程
        }
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
            // 先走 v2，并优先企业专属账号，避免落到「以前的个人钉钉账号」
            String userId = useridFromV2Mobile(token, normalized);
            if (!StringUtils.hasText(userId)) {
                userId = useridFromLegacyMobile(token, normalized);
            }
            if (StringUtils.hasText(userId)) {
                return identityOf(token, userId);
            }
            List<DingTalkDirectoryUserVO> directory = listDirectory();
            DingTalkDirectoryUserVO exclusiveHit = null;
            DingTalkDirectoryUserVO normalHit = null;
            for (DingTalkDirectoryUserVO row : directory) {
                if (!normalized.equals(normalizeMobile(row.getMobile()))) {
                    continue;
                }
                if (Boolean.TRUE.equals(row.getExclusiveAccount())) {
                    exclusiveHit = row;
                } else if (normalHit == null) {
                    normalHit = row;
                }
            }
            DingTalkDirectoryUserVO hit = exclusiveHit != null ? exclusiveHit : normalHit;
            if (hit != null) {
                if (StringUtils.hasText(hit.getUserid()) && StringUtils.hasText(hit.getUnionId())) {
                    return new DingIdentity(hit.getUserid(), hit.getUnionId());
                }
                if (StringUtils.hasText(hit.getUserid())) {
                    return identityOf(token, hit.getUserid());
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
        // 同一手机号可能同时有个人受邀账号与企业专属账号：优先专属账号
        String exclusiveId = firstExclusiveUserId(result.path("exclusive_account_userid_list"));
        if (StringUtils.hasText(exclusiveId)) {
            return exclusiveId;
        }
        return result.path("userid").asText("");
    }

    private String firstExclusiveUserId(JsonNode exclusive) {
        if (exclusive == null || exclusive.isMissingNode() || exclusive.isNull()) {
            return "";
        }
        if (exclusive.isArray()) {
            for (JsonNode item : exclusive) {
                if (StringUtils.hasText(item.asText(""))) {
                    return item.asText("");
                }
            }
            return "";
        }
        String raw = exclusive.asText("").trim();
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        if (raw.startsWith("[")) {
            try {
                JsonNode parsed = objectMapper.readTree(raw);
                if (parsed.isArray()) {
                    for (JsonNode item : parsed) {
                        if (StringUtils.hasText(item.asText(""))) {
                            return item.asText("");
                        }
                    }
                }
            } catch (Exception ignored) {
                return "";
            }
            return "";
        }
        return raw;
    }

    private DingIdentity identityOf(String token, String userId) throws Exception {
        ObjectNode get = objectMapper.createObjectNode();
        get.put("userid", userId);
        JsonNode detail = postJson("https://oapi.dingtalk.com/topapi/v2/user/get?access_token=" + encode(token), get);
        assertDingOk(detail, "查询钉钉用户详情失败");
        String unionId = detail.path("result").path("unionid").asText("");
        return StringUtils.hasText(unionId) ? new DingIdentity(userId, unionId) : null;
    }

    /** 用 unionId 换企业内 userid；找不到时返回空串。 */
    public String useridByUnionId(String unionId) {
        DingTalkAppService.Credential credential = credential();
        if (credential == null || !StringUtils.hasText(unionId)) {
            return "";
        }
        try {
            String token = legacyToken(credential);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("unionid", unionId.trim());
            JsonNode json = postJson("https://oapi.dingtalk.com/topapi/user/getbyunionid?access_token=" + encode(token), body);
            if (dingCode(json) != 0) {
                return "";
            }
            return json.path("result").path("userid").asText("");
        } catch (Exception ex) {
            return "";
        }
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

    private String resolveOrgSpaceId(String token, String unionId) throws Exception {
        String url = "https://api.dingtalk.com/v1.0/drive/spaces?unionId=" + encode(unionId)
                + "&spaceType=org&maxResults=50";
        JsonNode json = apiGet(token, url);
        JsonNode spaces = json.path("spaces");
        if (!spaces.isArray() || spaces.isEmpty()) {
            throw new BusinessException("未找到可用钉盘企业空间，请确认应用已开通钉盘权限且组织人已开通钉盘");
        }
        String first = spaces.get(0).path("spaceId").asText("");
        if (!StringUtils.hasText(first)) {
            throw new BusinessException("钉盘空间列表未返回 spaceId");
        }
        return first;
    }

    private JsonNode getDriveUploadInfo(String token, String spaceId, String unionId, String fileName,
                                        long fileSize, String md5) throws Exception {
        String url = "https://api.dingtalk.com/v1.0/drive/spaces/" + encode(spaceId)
                + "/files/0/uploadInfos?unionId=" + encode(unionId)
                + "&fileName=" + encode(fileName)
                + "&fileSize=" + fileSize
                + "&md5=" + encode(md5)
                + "&addConflictPolicy=autoRename";
        return apiGet(token, url);
    }

    private String putDriveFileBytes(String token, String spaceId, String unionId, String fileName, long fileSize,
                                     String md5, Path file, JsonNode uploadInfo) throws Exception {
        JsonNode headerInfo = uploadInfo.path("headerSignatureUploadInfo");
        if (headerInfo.isObject() && !headerInfo.isMissingNode()) {
            String resourceUrl = headerInfo.path("resourceUrl").asText("");
            String mediaId = headerInfo.path("mediaId").asText("");
            if (!StringUtils.hasText(resourceUrl)) {
                throw new BusinessException("钉盘上传信息缺少 resourceUrl");
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(resourceUrl))
                    .timeout(Duration.ofSeconds(60))
                    .PUT(HttpRequest.BodyPublishers.ofFile(file));
            JsonNode headers = headerInfo.path("headers");
            if (headers.isObject()) {
                Iterator<String> names = headers.fieldNames();
                while (names.hasNext()) {
                    String key = names.next();
                    String value = headers.path(key).asText("");
                    if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                        builder.header(key, value);
                    }
                }
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new BusinessException("钉盘 OSS 上传失败 HTTP " + response.statusCode()
                        + "：" + cut(response.body(), 160));
            }
            return mediaId;
        }
        JsonNode sts = uploadInfo.path("stsUploadInfo");
        if (sts.isObject() && !sts.isMissingNode()) {
            // 部分租户只返回 STS；用临时密钥直传 OSS
            String bucket = sts.path("bucket").asText("");
            String endPoint = sts.path("endPoint").asText("");
            String mediaId = sts.path("mediaId").asText("");
            String accessKeyId = sts.path("accessKeyId").asText("");
            String accessKeySecret = sts.path("accessKeySecret").asText("");
            String securityToken = sts.path("accessToken").asText("");
            if (!StringUtils.hasText(bucket) || !StringUtils.hasText(endPoint) || !StringUtils.hasText(mediaId)
                    || !StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)
                    || !StringUtils.hasText(securityToken)) {
                throw new BusinessException("钉盘 STS 上传信息不完整");
            }
            String host = endPoint.startsWith("http") ? endPoint.replaceFirst("^https?://", "") : endPoint;
            String objectKey = mediaId.startsWith("/") ? mediaId.substring(1) : mediaId;
            String putUrl = "https://" + bucket + "." + host + "/" + objectKey;
            String contentType = "application/octet-stream";
            String date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC));
            String canonicalizedOssHeaders = "x-oss-security-token:" + securityToken + "\n";
            String stringToSign = "PUT\n\n" + contentType + "\n" + date + "\n" + canonicalizedOssHeaders
                    + "/" + bucket + "/" + objectKey;
            String signature = hmacSha1Base64(accessKeySecret, stringToSign);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(putUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", contentType)
                    .header("Date", date)
                    .header("x-oss-security-token", securityToken)
                    .header("Authorization", "OSS " + accessKeyId + ":" + signature)
                    .PUT(HttpRequest.BodyPublishers.ofFile(file))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new BusinessException("钉盘 STS 上传失败 HTTP " + response.statusCode()
                        + "：" + cut(response.body(), 160));
            }
            return mediaId;
        }
        throw new BusinessException("钉盘未返回可用上传协议（需开通钉盘上传权限）：" + cut(uploadInfo.toString(), 180));
    }

    private JsonNode addDriveFile(String token, String spaceId, String unionId, String fileName, String mediaId)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("parentId", "0");
        body.put("fileType", "file");
        body.put("fileName", fileName);
        body.put("mediaId", mediaId);
        body.put("addConflictPolicy", "autoRename");
        body.put("unionId", unionId);
        return apiPost(token, "https://api.dingtalk.com/v1.0/drive/spaces/" + encode(spaceId) + "/files", body);
    }

    private static String buildDriveOpenUrl(String spaceId, String fileId, String fileName) {
        return "dingtalk://dingtalkclient/action/open_file?spaceId=" + encode(spaceId)
                + "&fileId=" + encode(fileId)
                + "&fileName=" + encode(fileName == null ? "resume" : fileName)
                + "&fileType=file";
    }

    private static String sanitizeDriveFileName(String fileName, Path file) {
        String name = StringUtils.hasText(fileName) ? fileName.trim() : file.getFileName().toString();
        name = name.replaceAll("[\\t\\r\\n*\"<>|]", "_").replaceAll("\\s+$", "");
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        if (!StringUtils.hasText(name)) {
            name = "resume.bin";
        }
        if (name.length() > 120) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 ? name.substring(dot) : "";
            name = name.substring(0, Math.min(120 - ext.length(), name.length())) + ext;
        }
        return name;
    }

    private static String md5Hex(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(Files.readAllBytes(file));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String hmacSha1Base64(String secret, String data) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private JsonNode apiGet(String token, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("x-acs-dingtalk-access-token", token)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
        if (response.statusCode() >= 300) {
            throw new BusinessException("钉钉接口失败 HTTP " + response.statusCode() + "：" + briefDingError(json, response.body()));
        }
        String code = json.path("code").asText("");
        if (StringUtils.hasText(code) && !"0".equals(code)) {
            throw new BusinessException("钉钉接口失败：" + briefDingError(json, response.body()));
        }
        return json;
    }

    private JsonNode apiPost(String token, String url, ObjectNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("x-acs-dingtalk-access-token", token)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
        if (response.statusCode() >= 300) {
            throw new BusinessException("钉钉接口失败 HTTP " + response.statusCode() + "：" + briefDingError(json, response.body()));
        }
        String code = json.path("code").asText("");
        if (StringUtils.hasText(code) && !"0".equals(code)) {
            throw new BusinessException("钉钉接口失败：" + briefDingError(json, response.body()));
        }
        return json;
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
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

    /** 已上传到钉盘的简历：日程描述挂 openUrl / fileId */
    public record DriveFile(String spaceId, String fileId, String fileName, String openUrl) {
    }

    public record DingIdentity(String userId, String unionId) {
    }

    public record BusyItem(String status, LocalDateTime start, LocalDateTime end) {
    }

    public record BusySchedule(String unionId, String error, List<BusyItem> items) {
    }
}
