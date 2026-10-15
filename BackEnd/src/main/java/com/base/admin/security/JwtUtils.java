package com.base.admin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, UUID.randomUUID().toString().replace("-", ""), true);
    }

    public String generateToken(Long userId, String username, String tokenId) {
        return generateToken(userId, username, tokenId, true);
    }

    /**
     * @param deviceExclusive true=参与单设备互踢（扫码/密码）；false=免登，不参与互踢
     */
    public String generateToken(Long userId, String username, String tokenId, boolean deviceExclusive) {
        return Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("deviceExclusive", deviceExclusive)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getTokenId(String token) {
        Claims claims = parseToken(token);
        return claims.getId();
    }

    /**
     * 是否参与设备互踢。旧 token 无该 claim 时按互踢会话处理（兼容已发 token）。
     */
    public boolean isDeviceExclusive(String token) {
        Boolean exclusive = parseToken(token).get("deviceExclusive", Boolean.class);
        return exclusive == null || exclusive;
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
