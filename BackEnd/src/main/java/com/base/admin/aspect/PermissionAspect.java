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

@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(requiresPermission)")
    public void checkPermission(JoinPoint joinPoint, RequiresPermission requiresPermission) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "未授权");
        }
        String requiredPerm = requiresPermission.value();
        if (!currentUser.getPermissions().contains(Constants.ADMIN_PERM)
                && !currentUser.getPermissions().contains(requiredPerm)) {
            throw new BusinessException(403, "权限不足，无法访问");
        }
    }
}
