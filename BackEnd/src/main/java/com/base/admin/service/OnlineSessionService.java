package com.base.admin.service;

import com.base.admin.domain.entity.SysUserOnline;
import com.base.admin.mapper.SysUserOnlineMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OnlineSessionService {

    private final SysUserOnlineMapper onlineMapper;

    @Value("${jwt.expiration}")
    private long expirationMs;

    public SysUserOnline findByUserId(Long userId) {
        return onlineMapper.selectById(userId);
    }

    public boolean hasActiveSession(Long userId) {
        SysUserOnline online = findByUserId(userId);
        if (online == null) {
            return false;
        }
        return online.getExpireTime() != null && online.getExpireTime().isAfter(LocalDateTime.now());
    }

    public boolean isTokenActive(Long userId, String tokenId) {
        return checkToken(userId, tokenId) == TokenStatus.ACTIVE;
    }

    /** 校验当前请求 token 与库内会话：有效 / 超时 / 被其他设备顶替 / 无会话。 */
    public TokenStatus checkToken(Long userId, String tokenId) {
        if (userId == null || tokenId == null || tokenId.isBlank()) {
            return TokenStatus.MISSING;
        }
        SysUserOnline online = findByUserId(userId);
        if (online == null) {
            return TokenStatus.MISSING;
        }
        if (online.getExpireTime() == null || !online.getExpireTime().isAfter(LocalDateTime.now())) {
            return TokenStatus.EXPIRED;
        }
        if (!tokenId.equals(online.getTokenId())) {
            return TokenStatus.REPLACED;
        }
        return TokenStatus.ACTIVE;
    }

    public void saveSession(Long userId, String username, String tokenId, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusSeconds(Math.max(expirationMs / 1000, 60));

        SysUserOnline online = new SysUserOnline();
        online.setUserId(userId);
        online.setUsername(username);
        online.setTokenId(tokenId);
        online.setIp(ip);
        online.setUserAgent(trimUa(userAgent));
        online.setLoginTime(now);
        online.setExpireTime(expireTime);
        // 并发登录用 upsert，避免 Duplicate entry
        onlineMapper.upsert(online);
    }

    public void removeSession(Long userId) {
        if (userId != null) {
            onlineMapper.physicalDeleteByUserId(userId);
        }
    }

    private String trimUa(String ua) {
        if (ua == null) {
            return "";
        }
        return ua.length() > 500 ? ua.substring(0, 500) : ua;
    }

    public enum TokenStatus {
        ACTIVE,
        EXPIRED,
        REPLACED,
        MISSING
    }
}
