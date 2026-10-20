package com.base.admin.robot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 群多轮填链 H5 票据：钉钉群不推未 @ 消息，改用卡片打开 H5 提交链接。
 */
@Component
public class RobotLinkFillTicketStore {

    private static final long TTL_MS = 2 * 60 * 60 * 1000L;

    private final ObjectMapper objectMapper;
    private final byte[] hmacKey;

    public RobotLinkFillTicketStore(
            ObjectMapper objectMapper,
            @Value("${jwt.secret:YmFzZS1hZG1pbi1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtMjU2LWJpdHMtbG9uZw==}") String jwtSecret) {
        this.objectMapper = objectMapper;
        this.hmacKey = (jwtSecret == null ? "" : jwtSecret).getBytes(StandardCharsets.UTF_8);
    }

    public record Ticket(
            String sessionKey,
            String staffId,
            Long userId,
            String openConversationId,
            String conversationType,
            long exp) {
    }

    public String issue(Ticket ticket) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("sk", ticket.sessionKey());
            node.put("sid", ticket.staffId() == null ? "" : ticket.staffId());
            if (ticket.userId() != null) {
                node.put("uid", ticket.userId());
            }
            node.put("cid", ticket.openConversationId() == null ? "" : ticket.openConversationId());
            node.put("ct", ticket.conversationType() == null ? "1" : ticket.conversationType());
            node.put("exp", System.currentTimeMillis() + TTL_MS);
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(node));
            return payload + "." + sign(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("签发填链票据失败: " + ex.getMessage(), ex);
        }
    }

    public Ticket parse(String ticket) {
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
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            var node = objectMapper.readTree(bytes);
            long exp = node.path("exp").asLong(0);
            if (exp > 0 && System.currentTimeMillis() > exp) {
                return null;
            }
            Long uid = node.has("uid") && !node.path("uid").isNull() ? node.path("uid").asLong() : null;
            return new Ticket(
                    node.path("sk").asText(""),
                    node.path("sid").asText(""),
                    uid,
                    node.path("cid").asText(""),
                    node.path("ct").asText("1"),
                    exp);
        } catch (Exception ex) {
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
