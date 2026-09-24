package com.base.admin.aspect;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Constants;
import com.base.admin.exception.BusinessException;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(requiresPermission)")
    public void checkPermission(JoinPoint joinPoint, RequiresPermission requiresPermission) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "未授权");
        }
        Set<String> permissions = currentUser.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            throw new BusinessException(403, "权限不足，无法访问");
        }
        if (permissions.contains(Constants.ADMIN_PERM)) {
            return;
        }
        boolean allowed = Arrays.stream(requiresPermission.value())
                .anyMatch(required -> matches(required, permissions));
        if (!allowed) {
            throw new BusinessException(403, "权限不足，无法访问");
        }
    }

    /**
     * 支持精确权限，以及前缀通配：{@code hr:*} 表示任意以 {@code hr:} 开头的权限。
     */
    static boolean matches(String required, Set<String> permissions) {
        if (required == null || required.isBlank()) {
            return false;
        }
        if (permissions.contains(required)) {
            return true;
        }
        if ("*:*:*".equals(required)) {
            return true;
        }
        // "hr:*" → 任意 hr:xxx
        if (required.endsWith(":*") && required.indexOf(':') == required.length() - 2) {
            String prefix = required.substring(0, required.length() - 1); // "hr:"
            return permissions.stream().anyMatch(p -> p != null && p.startsWith(prefix));
        }
        // "hr:record:*" → 任意 hr:record:xxx
        if (required.endsWith(":*")) {
            String prefix = required.substring(0, required.length() - 1);
            return permissions.stream().anyMatch(p -> p != null && p.startsWith(prefix));
        }
        return false;
    }
}
