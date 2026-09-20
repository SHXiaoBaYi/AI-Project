package com.base.admin.service;

import com.base.admin.config.DingTalkProperties;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class DingTalkCalendarClient {

    private static final DateTimeFormatter DING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DingTalkProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public CalendarCall createEvent(String unionId, String title, String description, LocalDateTime start, int durationMin) {
        if (!ready()) {
            return CalendarCall.fail("钉钉未配置。请在企业内部应用开通日程权限后填写 client-id 和 client-secret");
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
            return CalendarCall.fail("钉钉未配置，不能取消日程");
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
        if (!ready() || !StringUtils.hasText(mobile)) {
            return null;
        }
        try {
            String token = legacyToken();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("mobile", mobile);
            JsonNode byMobile = postForm("https://oapi.dingtalk.com/topapi/v2/user/getbymobile?access_token=" + encode(token), body);
            String userId = byMobile.path("result").path("userid").asText("");
            if (!StringUtils.hasText(userId)) {
                return null;
            }
            ObjectNode get = objectMapper.createObjectNode();
            get.put("userid", userId);
            JsonNode detail = postForm("https://oapi.dingtalk.com/topapi/v2/user/get?access_token=" + encode(token), get);
            String unionId = detail.path("result").path("unionid").asText("");
            return StringUtils.hasText(unionId) ? new DingIdentity(userId, unionId) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean ready() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getClientId())
                && StringUtils.hasText(properties.getClientSecret());
    }

    private String accessToken() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("appKey", properties.getClientId());
        body.put("appSecret", properties.getClientSecret());
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
            throw new IllegalStateException(response.body());
        }
        return token;
    }

    private String legacyToken() throws Exception {
        String url = "https://oapi.dingtalk.com/gettoken?appkey=" + encode(properties.getClientId())
                + "&appsecret=" + encode(properties.getClientSecret());
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        String token = json.path("access_token").asText("");
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException(response.body());
        }
        return token;
    }

    private JsonNode postForm(String url, ObjectNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
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
}
