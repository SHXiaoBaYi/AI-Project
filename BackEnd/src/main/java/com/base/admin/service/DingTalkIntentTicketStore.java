package com.base.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机器人 ActionCard → H5 意图票据。
 * <p>票据为自包含 HMAC 签名串，不依赖发券进程内存，本地 Stream + 线上 H5/API
 * （同一 jwt.secret）也可验签执行。jti 仅在本机做一次性防重放（尽力而为）。
 */
@Slf4j
@Component
public class DingTalkIntentTicketStore {

    private static final long TTL_MS = 30 * 60 * 1000L;
    private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final byte[] hmacKey;
    /** jti → expireAt，防同机重复点 */
    private final Map<String, Long> usedJti = new ConcurrentHashMap<>();

    public DingTalkIntentTicketStore(
            ObjectMapper objectMapper,
            @Value("${jwt.secret:YmFzZS1hZG1pbi1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtMjU2LWJpdHMtbG9uZw==}") String jwtSecret) {
        this.objectMapper = objectMapper;
        this.hmacKey = (jwtSecret == null ? "" : jwtSecret).getBytes(StandardCharsets.UTF_8);
    }

    public String issue(Intent intent) {
        try {
            long expireAt = System.currentTimeMillis() + TTL_MS;
            String jti = UUID.randomUUID().toString().replace("-", "");
            ObjectNode node = objectMapper.createObjectNode();
            node.put("jti", jti);
            node.put("exp", expireAt);
            node.put("k", intent.kind());
            node.put("a", intent.action());
            node.put("t", intent.targetUserId());
            node.put("s", intent.start().format(API_TIME));
            node.put("d", intent.durationMin());
            node.put("u", intent.senderUserId());
            if (StringUtils.hasText(intent.jobName())) {
                node.put("j", intent.jobName().trim());
            }
            if (intent.applicationId() != null) {
                node.put("app", intent.applicationId());
            }
            if (intent.roundNo() != null) {
                node.put("r", intent.roundNo());
            }
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(node));
            return payload + "." + sign(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("签发意图票据失败: " + ex.getMessage(), ex);
        }
    }

    public Intent peek(String ticket) {
        return parse(ticket, false);
    }

    public Intent consume(String ticket) {
        return parse(ticket, true);
    }

    private Intent parse(String ticket, boolean consume) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        String raw = ticket.trim();
        int dot = raw.lastIndexOf('.');
        if (dot <= 0 || dot >= raw.length() - 1) {
            return null;
        }
        String payload = raw.substring(0, dot);
        String sig = raw.substring(dot + 1);
        if (!sign(payload).equals(sig)) {
            log.warn("dingtalk intent ticket bad signature");
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
            long exp = node.path("exp").asLong(0L);
            if (exp < System.currentTimeMillis()) {
                return null;
            }
            String jti = node.path("jti").asText("");
            if (consume && StringUtils.hasText(jti)) {
                purgeUsed();
                Long prev = usedJti.putIfAbsent(jti, exp);
                if (prev != null) {
                    log.warn("dingtalk intent ticket already used jti={}", jti);
                    return null;
                }
            }
            Long appId = node.hasNonNull("app") ? node.path("app").asLong() : null;
            Integer round = node.hasNonNull("r") ? node.path("r").asInt() : null;
            String job = node.hasNonNull("j") ? node.path("j").asText() : null;
            return new Intent(
                    node.path("k").asText(""),
                    node.path("a").asText(""),
                    node.path("t").asLong(),
                    LocalDateTime.parse(node.path("s").asText(), API_TIME),
                    node.path("d").asInt(60),
                    job,
                    appId,
                    round,
                    node.path("u").asLong(),
                    exp);
        } catch (Exception ex) {
            log.warn("dingtalk intent ticket parse failed: {}", ex.getMessage());
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            byte[] dig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(dig);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC failed", ex);
        }
    }

    private void purgeUsed() {
        long now = System.currentTimeMillis();
        usedJti.entrySet().removeIf(e -> e.getValue() < now);
    }

    /**
     * @param kind prep=先建会/汇报或选人；invite=选定候选人后创建面试邀约
     */
    public record Intent(
            String kind,
            String action,
            Long targetUserId,
            LocalDateTime start,
            int durationMin,
            String jobName,
            Long applicationId,
            Integer roundNo,
            Long senderUserId,
            long expireAt
    ) {
        public Intent withExpireAt(long expire) {
            return new Intent(kind, action, targetUserId, start, durationMin, jobName,
                    applicationId, roundNo, senderUserId, expire);
        }
    }
}
