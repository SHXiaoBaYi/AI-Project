package com.base.admin.util;

import com.base.admin.security.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.function.Supplier;

/** 机器人回调等无 JWT 场景：临时以系统用户身份执行。 */
public final class RobotSecurity {

    private RobotSecurity() {
    }

    public static <T> T runAs(Long userId, String username, Supplier<T> action) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUsername(username == null || username.isBlank() ? "robot" : username);
        loginUser.setPermissions(Set.of());
        var auth = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
