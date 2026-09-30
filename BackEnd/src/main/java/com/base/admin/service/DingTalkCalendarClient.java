package com.base.admin.service;

import com.base.admin.config.DingTalkProperties;
import com.base.admin.domain.vo.DingTalkDirectoryUserVO;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DingTalkCalendarClient {

    private static final DateTimeFormatter DING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DingTalkProperties properties;
    private final DingTalkAppService dingTalkAppService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public CalendarCall createEvent(String unionId, String title, String description, LocalDateTime start,
                                    int durationMin, String location) {
        return createEvent(unionId, title, description, start, durationMin, location, List.of(), false, null);
    }

    /**
     * 在 owner 的主日历建日程；attendeeUnionIds 为额外参与人 unionId（不含 owner 也可再传）。
     * onlineMeeting=true 时写入钉钉日程扩展字段，尽量拉起线上会议能力。
     */
    public CalendarCall createEvent(String ownerUnionId, String title, String description, LocalDateTime start,
                                    int durationMin, String location, List<String> attendeeUnionIds, boolean onlineMeeting) {
        return createEvent(ownerUnionId, title, description, start, durationMin, location, attendeeUnionIds,
                onlineMeeting, null);
    }

    /**
     * @param richTextHtml 可选；用于简历链接卡片等富文本。为空时由 plain description 自动转 HTML。
     */
    public CalendarCall createEvent(String ownerUnionId, String title, String description, LocalDateTime start,
                                    int durationMin, String location, List<String> attendeeUnionIds,
                                    boolean onlineMeeting, String richTextHtml) {
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
            applyEventDescription(body, description, richTextHtml);
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
     * 将本地简历上传到钉钉应用存储空间（APP space）。
     * <p>优先 {@code dingtalk.resume-space-id}；否则
     * {@code POST /v1.0/storage/spaces}（ownerType=APP，scene 已存在则直接返回原空间），
     * 再走 Storage 上传。说明：{@code GET /drive/spaces?spaceType=app} 非法，官方仅支持 org。
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
            String unionId = ownerUnionId.trim();
            String configured = properties.getResumeSpaceId();
            if (StringUtils.hasText(configured)) {
                // 显式覆盖：按企业盘 Drive 上传
                return uploadResumeViaDriveApi(token, configured.trim(), unionId, name, size, md5, file);
            }
            String spaceId = resolveOrCreateAppStorageSpace(token, unionId);
            // APP 空间：应用权限开通后仍需给操作人临时授权，否则 uploadInfos 会 403 no privilege
            grantAppSpaceOperatorAccess(token, spaceId, unionId);
            return uploadResumeViaStorageApi(token, spaceId, unionId, name, size, md5, file);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("上传简历到钉盘失败：" + ex.getMessage());
        }
    }

    /**
     * 获取/创建应用存储空间：POST /v1.0/storage/spaces?unionId=…（ownerType=APP）。
     * 相同 scene+sceneId 已存在时钉钉直接返回原空间。
     */
    private String resolveOrCreateAppStorageSpace(String token, String unionId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode option = body.putObject("option");
        option.put("name", "HR简历");
        option.put("ownerType", "APP");
        option.put("scene", "hrresume");
        option.put("sceneId", "default");
        ObjectNode capabilities = option.putObject("capabilities");
        capabilities.put("canSearch", true);
        capabilities.put("canRename", false);
        capabilities.put("canRecordRecentFile", false);
        JsonNode json = apiPost(token, "https://api.dingtalk.com/v1.0/storage/spaces?unionId=" + encode(unionId), body);
        JsonNode space = json.path("space");
        String spaceId = firstText(space, "id", "spaceId");
        if (!StringUtils.hasText(spaceId)) {
            spaceId = firstText(json, "id", "spaceId");
        }
        if (!StringUtils.hasText(spaceId)) {
            throw new BusinessException("获取应用存储空间失败（请开通 Storage.Space.Write）："
                    + cut(json.toString(), 180));
        }
        return spaceId;
    }

    /**
     * APP 存储空间：给操作人授予根目录临时编辑权（最长 3600 秒）。
     * 官方说明：APP 空间任何人操作都需先授权；仅开通 Storage.UploadInfo.Read 不够。
     */
    private void grantAppSpaceOperatorAccess(String token, String spaceId, String unionId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("roleId", "MANAGER");
        ObjectNode option = body.putObject("option");
        option.put("duration", 3600);
        ArrayNode members = body.putArray("members");
        ObjectNode member = members.addObject();
        member.put("type", "USER");
        member.put("id", unionId);
        String corpId = dingTalkAppService.corpId();
        if (StringUtils.hasText(corpId)) {
            member.put("corpId", corpId);
        }
        try {
            apiPost(token, "https://api.dingtalk.com/v1.0/storage/spaces/" + encode(spaceId)
                    + "/dentries/0/permissions?unionId=" + encode(unionId), body);
        } catch (BusinessException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("403") || msg.contains("permissionDenied") || msg.contains("no privilege")) {
                throw new BusinessException(msg + "；APP 空间授权失败，请同时开通 Storage.Permission.Write"
                        + "（上传前需给操作人临时授权，仅开通 UploadInfo.Read 不够）");
            }
            throw ex;
        }
    }

    /** 钉盘 Drive 上传（配置覆盖 spaceId 时使用） */
    private DriveFile uploadResumeViaDriveApi(String token, String spaceId, String unionId, String name,
                                              long size, String md5, Path file) throws Exception {
        JsonNode uploadInfo = getDriveUploadInfo(token, spaceId, unionId, name, size, md5);
        String mediaId = putDriveFileBytes(file, uploadInfo);
        if (!StringUtils.hasText(mediaId)) {
            throw new BusinessException("钉盘上传未返回 mediaId：" + cut(uploadInfo.toString(), 180));
        }
        JsonNode added = addDriveFile(token, spaceId, unionId, name, mediaId);
        String fileId = firstText(added, "fileId", "id");
        String savedName = firstText(added, "fileName", "name");
        if (!StringUtils.hasText(savedName)) {
            savedName = name;
        }
        if (!StringUtils.hasText(fileId)) {
            throw new BusinessException("钉盘未返回 fileId：" + cut(added.toString(), 180));
        }
        String previewUrl = resolveDentryPreviewUrl(token, spaceId, fileId, unionId);
        String openUrl = StringUtils.hasText(previewUrl) ? previewUrl : buildDriveOpenUrl(spaceId, fileId, savedName);
        return new DriveFile(spaceId, fileId, savedName, openUrl, null, previewUrl);
    }

    /** 应用存储空间：Storage 上传信息 → OSS → commit */
    private DriveFile uploadResumeViaStorageApi(String token, String spaceId, String unionId, String name,
                                                long size, String md5, Path file) throws Exception {
        JsonNode uploadInfo = getStorageUploadInfo(token, spaceId, unionId, name, size, md5);
        String uploadKey = uploadInfo.path("uploadKey").asText("");
        if (!StringUtils.hasText(uploadKey)) {
            throw new BusinessException("钉盘上传未返回 uploadKey（请开通 Storage.UploadInfo.Read）："
                    + cut(uploadInfo.toString(), 180));
        }
        putStorageFileBytes(file, uploadInfo);
        JsonNode committed = commitStorageFile(token, spaceId, unionId, name, uploadKey, size);
        JsonNode dentry = committed.path("dentry");
        if (!dentry.isObject() || dentry.isMissingNode()) {
            dentry = committed;
        }
        String fileId = firstText(dentry, "id", "fileId");
        String savedName = firstText(dentry, "name", "fileName");
        if (!StringUtils.hasText(savedName)) {
            savedName = name;
        }
        String uuid = firstText(dentry, "uuid", "dentryUuid");
        if (!StringUtils.hasText(fileId)) {
            throw new BusinessException("钉盘提交文件未返回 fileId：" + cut(committed.toString(), 180));
        }
        String previewUrl = resolveDentryPreviewUrl(token, spaceId, fileId, unionId);
        String openUrl = StringUtils.hasText(previewUrl) ? previewUrl : buildDriveOpenUrl(spaceId, fileId, savedName);
        return new DriveFile(spaceId, fileId, savedName, openUrl, uuid, previewUrl);
    }

    /**
     * 获取钉盘文件 HTTPS 预览链接（PC/手机都可点开）。
     * 需 Storage.File.Read；失败时返回空，调用方回退 dingtalk://。
     */
    private String resolveDentryPreviewUrl(String token, String spaceId, String dentryId, String unionId) {
        if (!StringUtils.hasText(spaceId) || !StringUtils.hasText(dentryId) || !StringUtils.hasText(unionId)) {
            return "";
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode option = body.putObject("option");
            option.put("type", "PREVIEW");
            option.put("checkLogin", true);
            option.put("waterMark", false);
            JsonNode json = apiPost(token, "https://api.dingtalk.com/v1.0/storage/spaces/" + encode(spaceId)
                    + "/dentries/" + encode(dentryId) + "/openInfos/query?unionId=" + encode(unionId), body);
            String url = firstText(json, "url");
            return StringUtils.hasText(url) && url.startsWith("http") ? url.trim() : "";
        } catch (Exception ex) {
            log.warn("resolve dentry preview url failed spaceId={} dentryId={}: {}", spaceId, dentryId, ex.getMessage());
            return "";
        }
    }

    /**
     * 日程描述：同时写 plain description + richTextDescription（HTML）。
     * PC 端对 description 里的 dingtalk:// 基本不可点；HTTPS + HTML a 标签才能打开。
     * @param richTextHtml 若传入则直接使用（用于简历卡片）；否则由 plain 自动转 HTML。
     */
    private void applyEventDescription(ObjectNode body, String description, String richTextHtml) {
        String plain = description == null ? "" : description.trim();
        body.put("description", plain);
        String rich = StringUtils.hasText(richTextHtml) ? richTextHtml.trim() : "";
        if (!StringUtils.hasText(rich) && StringUtils.hasText(plain)) {
            rich = toCalendarRichTextHtml(plain);
        }
        if (!StringUtils.hasText(rich)) {
            return;
        }
        ObjectNode richNode = body.putObject("richTextDescription");
        richNode.put("text", rich);
    }

    /**
     * 简历链接卡片（放在描述第一行）+ 正文。
     * 卡片标题用简历文件名，href 用 HTTPS 预览链接；富文本里做成灰底圆角块，接近附件卡片观感。
     */
    public static String buildResumeLinkCardRichText(String cardTitle, String cardUrl, String bodyPlain) {
        String title = StringUtils.hasText(cardTitle) ? cardTitle.trim() : "简历";
        String url = StringUtils.hasText(cardUrl) ? cardUrl.trim() : "";
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"__aliyun_email_body_block\">");
        if (StringUtils.hasText(url) && url.startsWith("http")) {
            html.append("<div style=\"clear:both;margin:0 0 12px 0;padding:12px 14px;")
                    .append("background:#F7F8FA;border:1px solid #E5E6EB;border-radius:8px;\">")
                    .append("<div style=\"font-size:12px;color:#86909C;margin-bottom:4px;\">附件 · 简历</div>")
                    .append("<a href=\"").append(escapeHtml(url)).append("\" ")
                    .append("style=\"color:#1677FF;text-decoration:none;font-size:14px;font-weight:600;\">")
                    .append(escapeHtml(title)).append("</a>")
                    .append("<div style=\"margin-top:6px;font-size:12px;color:#86909C;\">点击打开</div>")
                    .append("</div>");
        } else {
            html.append("<div style=\"clear:both;margin:0 0 12px 0;padding:12px 14px;")
                    .append("background:#F7F8FA;border:1px solid #E5E6EB;border-radius:8px;\">")
                    .append("<div style=\"font-size:14px;font-weight:600;color:#1D2129;\">")
                    .append(escapeHtml(title)).append("</div>")
                    .append("</div>");
        }
        if (StringUtils.hasText(bodyPlain)) {
            for (String line : bodyPlain.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                html.append("<div style=\"clear:both;\">");
                html.append(linkifyAndEscape(line));
                html.append("</div>");
            }
        }
        html.append("</div>");
        return html.toString();
    }

    /** 把纯文本描述转成日程富文本 HTML；自动把 http(s) 链接变成可点击 a 标签。 */
    static String toCalendarRichTextHtml(String plain) {
        String[] lines = plain.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"__aliyun_email_body_block\">");
        for (String line : lines) {
            html.append("<div style=\"clear:both;\">");
            html.append(linkifyAndEscape(line));
            html.append("</div>");
        }
        html.append("</div>");
        return html.toString();
    }

    private static String linkifyAndEscape(String line) {
        if (line == null || line.isEmpty()) {
            return "<br/>";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(https?://\\S+)")
                .matcher(line);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(escapeHtml(line.substring(last, m.start())));
            String url = m.group(1);
            while (url.endsWith("）") || url.endsWith(")") || url.endsWith("。") || url.endsWith(",") || url.endsWith("，")) {
                url = url.substring(0, url.length() - 1);
            }
            out.append("<a href=\"").append(escapeHtml(url)).append("\">").append(escapeHtml(url)).append("</a>");
            last = m.start() + url.length();
            if (last < m.end()) {
                out.append(escapeHtml(line.substring(last, m.end())));
                last = m.end();
            }
        }
        out.append(escapeHtml(line.substring(last)));
        return out.toString();
    }

    private static String escapeHtml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 给面试官/抄送人授予钉盘文件「可查看/下载」权限（best-effort，失败不抛）。
     * memberId 需为钉钉 staffId（userid）。优先走 Storage 权限接口。
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
            if (StringUtils.hasText(driveFile.uuid())) {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("roleId", "DOWNLOADER");
                // APP 空间只支持临时授权
                ObjectNode option = body.putObject("option");
                option.put("duration", 30L * 24 * 3600);
                ArrayNode members = body.putArray("members");
                for (String staffId : ids) {
                    ObjectNode m = members.addObject();
                    m.put("type", "USER");
                    m.put("id", staffId);
                    m.put("corpId", corpId);
                }
                apiPost(token, "https://api.dingtalk.com/v2.0/storage/spaces/dentries/"
                        + encode(driveFile.uuid()) + "/permissions?unionId=" + encode(ownerUnionId.trim()), body);
                return;
            }
            // 无 uuid 时回退旧 Drive 权限接口（best-effort）
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
            applyEventDescription(body, description, null);
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

    /** Drive：获取文件上传信息（应用盘 / 企业盘） */
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

    private String putDriveFileBytes(Path file, JsonNode uploadInfo) throws Exception {
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
        throw new BusinessException("钉盘未返回可用上传协议：" + cut(uploadInfo.toString(), 180));
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

    private JsonNode getStorageUploadInfo(String token, String spaceId, String unionId, String fileName,
                                          long fileSize, String md5) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("protocol", "HEADER_SIGNATURE");
        body.put("multipart", false);
        ObjectNode option = body.putObject("option");
        option.put("storageDriver", "DINGTALK");
        ObjectNode preCheck = option.putObject("preCheckParam");
        preCheck.put("md5", md5);
        preCheck.put("size", fileSize);
        preCheck.put("parentId", "0");
        preCheck.put("name", fileName);
        return apiPost(token, "https://api.dingtalk.com/v1.0/storage/spaces/" + encode(spaceId)
                + "/files/uploadInfos/query?unionId=" + encode(unionId), body);
    }

    private void putStorageFileBytes(Path file, JsonNode uploadInfo) throws Exception {
        JsonNode headerInfo = uploadInfo.path("headerSignatureInfo");
        if (!headerInfo.isObject() || headerInfo.isMissingNode()) {
            headerInfo = uploadInfo.path("headerSignatureUploadInfo");
        }
        if (!headerInfo.isObject() || headerInfo.isMissingNode()) {
            throw new BusinessException("钉盘未返回 Header 加签上传信息（需 Storage.UploadInfo.Read）："
                    + cut(uploadInfo.toString(), 180));
        }
        String resourceUrl = "";
        JsonNode urls = headerInfo.path("resourceUrls");
        if (urls.isArray() && !urls.isEmpty()) {
            resourceUrl = urls.get(0).asText("");
        }
        if (!StringUtils.hasText(resourceUrl)) {
            resourceUrl = headerInfo.path("resourceUrl").asText("");
        }
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
    }

    private JsonNode commitStorageFile(String token, String spaceId, String unionId, String fileName,
                                       String uploadKey, long fileSize) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("uploadKey", uploadKey);
        body.put("name", fileName);
        body.put("parentId", "0");
        ObjectNode option = body.putObject("option");
        option.put("size", fileSize);
        option.put("conflictStrategy", "AUTO_RENAME");
        return apiPost(token, "https://api.dingtalk.com/v1.0/storage/spaces/" + encode(spaceId)
                + "/files/commit?unionId=" + encode(unionId), body);
    }

    private static String buildDriveOpenUrl(String spaceId, String fileId, String fileName) {
        return "dingtalk://dingtalkclient/action/open_file?spaceId=" + encode(spaceId)
                + "&fileId=" + encode(fileId)
                + "&fileName=" + encode(fileName == null ? "resume" : fileName)
                + "&fileType=file";
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
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
            throw new BusinessException(dingHttpFail(response.statusCode(), url, json, response.body()));
        }
        String code = json.path("code").asText("");
        if (StringUtils.hasText(code) && !"0".equals(code)) {
            throw new BusinessException("钉钉接口失败：" + briefDingError(json, response.body())
                    + hintForPrivilege(url, briefDingError(json, response.body())));
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
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode json = objectMapper.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
        if (response.statusCode() >= 300) {
            throw new BusinessException(dingHttpFail(response.statusCode(), url, json, response.body()));
        }
        String code = json.path("code").asText("");
        if (StringUtils.hasText(code) && !"0".equals(code)) {
            throw new BusinessException("钉钉接口失败：" + briefDingError(json, response.body())
                    + hintForPrivilege(url, briefDingError(json, response.body())));
        }
        return json;
    }

    private static String dingHttpFail(int status, String url, JsonNode json, String raw) {
        String brief = briefDingError(json, raw);
        String path = url == null ? "" : url.replaceFirst("^https://api\\.dingtalk\\.com", "");
        int q = path.indexOf('?');
        if (q > 0) {
            path = path.substring(0, q);
        }
        return "钉钉接口失败 HTTP " + status + "：" + brief
                + (StringUtils.hasText(path) ? "（" + path + "）" : "")
                + hintForPrivilege(url, brief);
    }

    private static String hintForPrivilege(String url, String brief) {
        if (url == null || brief == null) {
            return "";
        }
        String lower = brief.toLowerCase();
        if (!(lower.contains("privilege") || lower.contains("permission") || lower.contains("权限") || lower.contains("forbidden"))) {
            return "";
        }
        if (url.contains("/storage/spaces") && !url.contains("/files/") && !url.contains("/dentries/")) {
            return "；请开通 Storage.Space.Write";
        }
        if (url.contains("openInfos") && url.contains("/storage/")) {
            return "；请开通 Storage.File.Read（获取简历 HTTPS 预览链接，PC 端可点开）";
        }
        if (url.contains("uploadInfos") && url.contains("/storage/")) {
            return "；APP 空间需先给操作人临时授权（代码会自动授权）；请确认已开通 Storage.UploadInfo.Read"
                    + " 与 Storage.Permission.Write，且权限已审批生效";
        }
        if (url.contains("/files/commit")) {
            return "；请开通 Storage.File.Write";
        }
        if (url.contains("/permissions")) {
            return "；请开通 Storage.Permission.Write（APP 空间上传前给操作人临时授权）";
        }
        if (url.contains("/drive/spaces") && url.contains("uploadInfos")) {
            return "；请确认应用具备钉盘上传权限，且操作人对该空间有上传权";
        }
        if (url.contains("/drive/spaces")) {
            return "；GET /drive/spaces 的 spaceType 仅支持 org，应用空间请用 Storage 添加空间接口";
        }
        return "；请检查钉钉应用权限是否已审批生效";
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

    /** 已上传到钉盘的简历：优先用 HTTPS 预览链接（PC 可点）；uuid 用于 Storage 授权 */
    public record DriveFile(String spaceId, String fileId, String fileName, String openUrl, String uuid,
                            String previewUrl) {
        public DriveFile(String spaceId, String fileId, String fileName, String openUrl, String uuid) {
            this(spaceId, fileId, fileName, openUrl, uuid, null);
        }
    }

    public record DingIdentity(String userId, String unionId) {
    }

    public record BusyItem(String status, LocalDateTime start, LocalDateTime end) {
    }

    public record BusySchedule(String unionId, String error, List<BusyItem> items) {
    }
}
