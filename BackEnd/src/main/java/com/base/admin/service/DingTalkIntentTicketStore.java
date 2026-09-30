package com.base.admin.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机器人 ActionCard → H5 意图短码（免登后执行建日程 / 选候选人）。
 */
@Component
public class DingTalkIntentTicketStore {

    private static final long TTL_MS = 30 * 60 * 1000L;

    private final Map<String, Intent> tickets = new ConcurrentHashMap<>();

    public String issue(Intent intent) {
        purgeExpired();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, intent.withExpireAt(System.currentTimeMillis() + TTL_MS));
        return ticket;
    }

    public Intent peek(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        Intent entry = tickets.get(ticket.trim());
        if (entry == null || entry.expireAt() < System.currentTimeMillis()) {
            return null;
        }
        return entry;
    }

    public Intent consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        Intent entry = tickets.remove(ticket.trim());
        if (entry == null || entry.expireAt() < System.currentTimeMillis()) {
            return null;
        }
        return entry;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue().expireAt() < now);
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
