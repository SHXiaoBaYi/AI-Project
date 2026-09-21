package com.base.admin.service;

import com.base.admin.config.DingTalkProperties;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DingTalkCalendarClient {

    private static final DateTimeFormatter DING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DingTalkProperties properties;
    private final DingTalkAppService dingTalkAppService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public CalendarCall createEvent(String unionId, String title, String description, LocalDateTime start, int durationMin) {
        if (!ready()) {
            return CalendarCall.fail("钉钉未配置。请到「系统管理 → 钉钉应用配置」填写并启用");
        }
        if (!StringUtils.hasText(unionId)) {
            return CalendarCall.fail("面试官没有钉钉 unionId，不能建日程");
        }
        try {
            String token = accessToken();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("summary", title);
            body.put("description", description);
            body.put("isAllDay", false);
            body.set("start", timeNode(start));
            body.set("end", timeNode(start.plusMinutes(durationMin)));
            ObjectNode attendee = objectMapper.createObjectNode();
            attendee.put("id", unionId);
            body.putArray("attendees").add(attendee);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.dingtalk.com/v1.0/calendar/users/" + encode(unionId) + "/calendars/primary/events"))
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

    public boolean configured() {
        return ready();
    }

    public DingIdentity resolveByMobile(String mobile) {
        DingTalkAppService.Credential credential = credential();
        if (credential == null || !StringUtils.hasText(mobile)) {
            return null;
        }
        try {
            String token = legacyToken(credential);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("mobile", mobile.trim());
            JsonNode byMobile = postJson("https://oapi.dingtalk.com/topapi/v2/user/getbymobile?access_token=" + encode(token), body);
            assertDingOk(byMobile, "按手机号查询钉钉用户失败");
            String userId = byMobile.path("result").path("userid").asText("");
            if (!StringUtils.hasText(userId)) {
                return null;
            }
            ObjectNode get = objectMapper.createObjectNode();
            get.put("userid", userId);
            JsonNode detail = postJson("https://oapi.dingtalk.com/topapi/v2/user/get?access_token=" + encode(token), get);
            assertDingOk(detail, "查询钉钉用户详情失败");
            String unionId = detail.path("result").path("unionid").asText("");
            return StringUtils.hasText(unionId) ? new DingIdentity(userId, unionId) : null;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("钉钉查询失败：" + ex.getMessage());
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
        return end ? day.atStartOfDay() : day.atStartOfDay();
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

    private static void assertDingOk(JsonNode json, String fallback) {
        int code = json.path("errcode").asInt(0);
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

    public record DingIdentity(String userId, String unionId) {
    }

    public record BusyItem(String status, LocalDateTime start, LocalDateTime end) {
    }

    public record BusySchedule(String unionId, String error, List<BusyItem> items) {
    }
}
