package com.base.admin.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钉钉 authCode 一次性：冲突确认前已换过码，强制登录改用短时票据。
 */
@Component
public class DingTalkForceLoginTicketStore {

    private static final long TTL_MS = 5 * 60 * 1000L;

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

    public String issue(Long userId, String username) {
        purgeExpired();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Entry(userId, username, System.currentTimeMillis() + TTL_MS));
        return ticket;
    }

    public Entry consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        Entry entry = tickets.remove(ticket.trim());
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() < System.currentTimeMillis()) {
            return null;
        }
        return entry;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }

    public record Entry(Long userId, String username, long expireAt) {
    }
}
