package com.base.admin.service;

import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 钉钉 H5 JSAPI 鉴权（dd.config），供语音录制等能力使用。
 */
@Service
@RequiredArgsConstructor
public class DingTalkJsapiService {

    private final DingTalkAppService dingTalkAppService;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final AtomicReference<CachedTicket> ticketCache = new AtomicReference<>();

    public Map<String, Object> sign(String pageUrl) {
        if (!StringUtils.hasText(pageUrl)) {
            throw new BusinessException("缺少鉴权 URL");
        }
        DingTalkAppService.Credential credential = dingTalkAppService.credential();
        if (credential == null) {
            throw new BusinessException("钉钉应用未配置或未启用");
        }
        String corpId = dingTalkAppService.corpId();
        if (!StringUtils.hasText(corpId)) {
            throw new BusinessException("未配置企业 CorpId");
        }
        Long agentId = dingTalkAppService.agentId();
        if (agentId == null) {
            throw new BusinessException("未配置 AgentId，无法做 JSAPI 鉴权");
        }
        try {
            String ticket = jsapiTicket(credential);
            String nonceStr = UUID.randomUUID().toString().replace("-", "");
            String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
            String url = normalizeUrl(pageUrl);
            String plain = "jsapi_ticket=" + ticket
                    + "&noncestr=" + nonceStr
                    + "&timestamp=" + timeStamp
                    + "&url=" + url;
            String signature = sha1Hex(plain);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("agentId", String.valueOf(agentId));
            map.put("corpId", corpId);
            map.put("timeStamp", timeStamp);
            map.put("nonceStr", nonceStr);
            map.put("signature", signature);
            return map;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("JSAPI 鉴权失败：" + e.getMessage());
        }
    }

    private String jsapiTicket(DingTalkAppService.Credential credential) throws Exception {
        CachedTicket cached = ticketCache.get();
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAtMs > now + 60_000) {
            return cached.ticket;
        }
        String token = appAccessToken(credential);
        String url = "https://oapi.dingtalk.com/get_jsapi_ticket?access_token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode json = objectMapper.readTree(response.body() == null ? "{}" : response.body());
        if (json.path("errcode").asInt(-1) != 0) {
            throw new BusinessException("获取 jsapi_ticket 失败：" + json.path("errmsg").asText("unknown"));
        }
        String ticket = json.path("ticket").asText("");
        if (!StringUtils.hasText(ticket)) {
            throw new BusinessException("jsapi_ticket 为空");
        }
        int expiresIn = json.path("expires_in").asInt(7200);
        ticketCache.set(new CachedTicket(ticket, now + Math.max(60, expiresIn - 120) * 1000L));
        return ticket;
    }

    private String appAccessToken(DingTalkAppService.Credential credential) throws Exception {
        // 与免登一致：新版 oauth2 accessToken
        var body = objectMapper.createObjectNode();
        body.put("appKey", credential.clientId());
        body.put("appSecret", credential.clientSecret());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode json = objectMapper.readTree(response.body() == null ? "{}" : response.body());
        String token = json.path("accessToken").asText("");
        if (!StringUtils.hasText(token)) {
            // 兜底旧接口
            String legacy = "https://oapi.dingtalk.com/gettoken?appkey="
                    + URLEncoder.encode(credential.clientId(), StandardCharsets.UTF_8)
                    + "&appsecret=" + URLEncoder.encode(credential.clientSecret(), StandardCharsets.UTF_8);
            HttpRequest req2 = HttpRequest.newBuilder().uri(URI.create(legacy)).timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> res2 = http.send(req2, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode j2 = objectMapper.readTree(res2.body() == null ? "{}" : res2.body());
            token = j2.path("access_token").asText("");
            if (!StringUtils.hasText(token)) {
                throw new BusinessException("获取 accessToken 失败");
            }
        }
        return token;
    }

    /** 签名用 URL：去掉 hash，保留 query */
    private static String normalizeUrl(String raw) {
        String url = raw.trim();
        int hash = url.indexOf('#');
        if (hash >= 0) {
            url = url.substring(0, hash);
        }
        return url;
    }

    private static String sha1Hex(String plain) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private record CachedTicket(String ticket, long expireAtMs) {
    }
}
