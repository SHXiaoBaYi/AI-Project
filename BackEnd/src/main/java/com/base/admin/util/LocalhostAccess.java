package com.base.admin.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 本机访问判定：用于密码登录暗门、数据权限配置页暗门。
 * 信任 Host / Origin / Referer / X-Forwarded-Host 中是否出现 localhost 或 127.0.0.1。
 */
public final class LocalhostAccess {

    public static final String DATA_SCOPE_OPERATOR = "bella";

    private LocalhostAccess() {
    }

    public static boolean isLocalhostRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        // 浏览器访问以页面 Origin/Referer 为准，避免前端代理到本机后端时误开暗门
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        if (StringUtils.hasText(origin) || StringUtils.hasText(referer)) {
            return isLocalOrigin(origin) || isLocalOrigin(referer);
        }
        return isLocalHostValue(request.getHeader("Host"))
                || isLocalHostValue(request.getHeader("X-Forwarded-Host"))
                || isLocalHostValue(request.getServerName());
    }

    /** 数据权限配置页：localhost 或用户名为 bella（不看超管权限） */
    public static boolean canAccessDataScope(HttpServletRequest request, String username) {
        if (isLocalhostRequest(request)) {
            return true;
        }
        return StringUtils.hasText(username) && DATA_SCOPE_OPERATOR.equalsIgnoreCase(username.trim());
    }

    public static boolean isDataScopeOperator(String username) {
        return StringUtils.hasText(username) && DATA_SCOPE_OPERATOR.equalsIgnoreCase(username.trim());
    }

    private static boolean isLocalOrigin(String originOrReferer) {
        if (!StringUtils.hasText(originOrReferer)) {
            return false;
        }
        String v = originOrReferer.trim().toLowerCase();
        return v.contains("://localhost") || v.contains("://127.0.0.1");
    }

    private static boolean isLocalHostValue(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String h = host.trim().toLowerCase();
        int colon = h.indexOf(':');
        if (colon > 0) {
            h = h.substring(0, colon);
        }
        return "localhost".equals(h) || "127.0.0.1".equals(h) || "[::1]".equals(h) || "::1".equals(h);
    }
}
