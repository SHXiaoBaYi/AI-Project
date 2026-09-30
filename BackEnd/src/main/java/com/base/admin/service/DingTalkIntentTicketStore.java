package com.base.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            List<Long> targets = intent.targetUserIds() == null ? List.of() : intent.targetUserIds();
            if (targets.size() > 1 || (targets.size() == 1 && !Objects.equals(targets.getFirst(), intent.targetUserId()))) {
                ArrayNode ts = node.putArray("ts");
                for (Long id : targets) {
                    if (id != null) {
                        ts.add(id);
                    }
                }
            } else if (intent.targetUserId() != null) {
                // 单人也写一份，便于统一解析
                ArrayNode ts = node.putArray("ts");
                ts.add(intent.targetUserId());
            }
            List<Slot> slots = intent.slots() == null ? List.of() : intent.slots();
            if (!slots.isEmpty()) {
                ArrayNode arr = node.putArray("ss");
                for (Slot slot : slots) {
                    if (slot == null || slot.start() == null) {
                        continue;
                    }
                    ObjectNode one = arr.addObject();
                    one.put("s", slot.start().format(API_TIME));
                    one.put("d", slot.durationMin() <= 0 ? intent.durationMin() : slot.durationMin());
                }
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
            LocalDateTime start = LocalDateTime.parse(node.path("s").asText(), API_TIME);
            int duration = node.path("d").asInt(60);
            List<Slot> slots = new ArrayList<>();
            JsonNode ss = node.get("ss");
            if (ss != null && ss.isArray()) {
                for (JsonNode item : ss) {
                    String s = item.path("s").asText(null);
                    if (!StringUtils.hasText(s)) {
                        continue;
                    }
                    slots.add(new Slot(LocalDateTime.parse(s, API_TIME), item.path("d").asInt(duration)));
                }
            }
            if (slots.isEmpty()) {
                slots.add(new Slot(start, duration));
            }
            List<Long> targetIds = new ArrayList<>();
            JsonNode ts = node.get("ts");
            if (ts != null && ts.isArray()) {
                for (JsonNode item : ts) {
                    if (item != null && item.canConvertToLong()) {
                        targetIds.add(item.asLong());
                    }
                }
            }
            long primaryTarget = node.path("t").asLong();
            if (targetIds.isEmpty() && primaryTarget > 0) {
                targetIds.add(primaryTarget);
            }
            return new Intent(
                    node.path("k").asText(""),
                    node.path("a").asText(""),
                    primaryTarget,
                    start,
                    duration,
                    job,
                    appId,
                    round,
                    node.path("u").asLong(),
                    slots,
                    targetIds,
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

    public record Slot(LocalDateTime start, int durationMin) {
    }

    /**
     * @param kind prep=打开系统表单；invite=选定候选人后创建面试邀约
     * @param slots 机器人推荐时段（表单内单选，默认第一项）
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
            List<Slot> slots,
            List<Long> targetUserIds,
            long expireAt
    ) {
        public Intent withExpireAt(long expire) {
            return new Intent(kind, action, targetUserId, start, durationMin, jobName,
                    applicationId, roundNo, senderUserId, slots, targetUserIds, expire);
        }
    }
}
