package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        if (userId == null || tokenId == null) {
            return false;
        }
        SysUserOnline online = findByUserId(userId);
        if (online == null) {
            return false;
        }
        if (online.getExpireTime() == null || online.getExpireTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        return tokenId.equals(online.getTokenId());
    }

    public void saveSession(Long userId, String username, String tokenId, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusSeconds(Math.max(expirationMs / 1000, 60));

        SysUserOnline online = findByUserId(userId);
        if (online == null) {
            online = new SysUserOnline();
            online.setUserId(userId);
            online.setUsername(username);
            online.setTokenId(tokenId);
            online.setIp(ip);
            online.setUserAgent(trimUa(userAgent));
            online.setLoginTime(now);
            online.setExpireTime(expireTime);
            online.setIsActive(1);
            onlineMapper.insert(online);
        } else {
            online.setUsername(username);
            online.setTokenId(tokenId);
            online.setIp(ip);
            online.setUserAgent(trimUa(userAgent));
            online.setLoginTime(now);
            online.setExpireTime(expireTime);
            onlineMapper.updateById(online);
        }
    }

    public void removeSession(Long userId) {
        if (userId != null) {
            onlineMapper.deleteById(userId);
        }
    }

    public void removeByTokenId(String tokenId) {
        if (tokenId == null) {
            return;
        }
        onlineMapper.delete(new LambdaQueryWrapper<SysUserOnline>().eq(SysUserOnline::getTokenId, tokenId));
    }

    private String trimUa(String ua) {
        if (ua == null) {
            return "";
        }
        return ua.length() > 500 ? ua.substring(0, 500) : ua;
    }
}
