package com.base.admin.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 本机访问判定：用于密码登录暗门、数据权限配置页暗门。
 * 信任 Host / Origin / Referer / X-Forwarded-Host 中是否出现 localhost 或 127.0.0.1。
 */
public final class LocalhostAccess {

    /** @deprecated 保留兼容；请用 {@link #isDataScopeOperator(String)} */
    public static final String DATA_SCOPE_OPERATOR = "bella";

    /** 生产/线上可进「数据权限配置」的用户名（不看超管权限） */
    private static final Set<String> DATA_SCOPE_OPERATORS = Set.of("bella", "wangfangyang");

    /** 电商运营暗门仍仅 bella（与数据权限名单解耦，避免误开） */
    private static final Set<String> ECOM_OPERATORS = Set.of("bella");

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

    /** 数据权限配置页：localhost 或名单内用户名 */
    public static boolean canAccessDataScope(HttpServletRequest request, String username) {
        if (isLocalhostRequest(request)) {
            return true;
        }
        return isDataScopeOperator(username);
    }

    /** 电商运营暗门：localhost 或 bella */
    public static boolean canAccessEcom(HttpServletRequest request, String username) {
        if (isLocalhostRequest(request)) {
            return true;
        }
        return isEcomOperator(username);
    }

    public static boolean isDataScopeOperator(String username) {
        return matchesOperator(username, DATA_SCOPE_OPERATORS);
    }

    public static boolean isEcomOperator(String username) {
        return matchesOperator(username, ECOM_OPERATORS);
    }

    private static boolean matchesOperator(String username, Set<String> operators) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return operators.contains(username.trim().toLowerCase(Locale.ROOT));
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
