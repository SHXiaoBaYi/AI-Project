package com.base.admin.service;

import com.base.admin.domain.dto.UserDataScopeSaveDTO;
import com.base.admin.domain.vo.UserDataScopeVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.LocalhostAccess;
import com.base.admin.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDataScopeService {

    public static final String MODE_DEFAULT = "DEFAULT";
    public static final String MODE_PERSON = "PERSON";
    public static final String MODE_SELF = "SELF";

    private final JdbcTemplate jdbc;

    public void requireAccess() {
        var user = SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(403, "无权访问数据权限配置");
        }
        HttpServletRequest request = currentRequest();
        if (!LocalhostAccess.canAccessDataScope(request, user.getUsername())) {
            throw new BusinessException(403, "无权访问数据权限配置");
        }
    }

    public boolean currentUserCanAccess() {
        var user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return false;
        }
        return LocalhostAccess.canAccessDataScope(currentRequest(), user.getUsername());
    }

    public List<UserDataScopeVO> listUsers() {
        requireAccess();
        return jdbc.query("""
                SELECT u.user_id, u.username, u.nickname,
                       COALESCE(s.mode, 'DEFAULT') AS mode
                FROM sys_user u
                LEFT JOIN sys_user_data_scope s ON s.user_id = u.user_id AND s.is_active = 1
                WHERE u.is_active = 1 AND u.status = 0
                ORDER BY u.nickname, u.username
                """, (rs, i) -> {
            UserDataScopeVO vo = new UserDataScopeVO();
            vo.setUserId(rs.getLong("user_id"));
            vo.setUsername(rs.getString("username"));
            vo.setNickname(rs.getString("nickname"));
            vo.setMode(rs.getString("mode"));
            return vo;
        });
    }

    public UserDataScopeVO get(Long userId) {
        requireAccess();
        if (userId == null) {
            throw new BusinessException("请选择用户");
        }
        UserDataScopeVO vo = jdbc.query("""
                SELECT u.user_id, u.username, u.nickname, COALESCE(s.mode, 'DEFAULT') AS mode
                FROM sys_user u
                LEFT JOIN sys_user_data_scope s ON s.user_id = u.user_id AND s.is_active = 1
                WHERE u.user_id = ? AND u.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            UserDataScopeVO row = new UserDataScopeVO();
            row.setUserId(rs.getLong("user_id"));
            row.setUsername(rs.getString("username"));
            row.setNickname(rs.getString("nickname"));
            row.setMode(rs.getString("mode"));
            return row;
        }, userId);
        if (vo == null) {
            throw new BusinessException("用户不存在");
        }
        List<Long> targets = jdbc.query("""
                SELECT target_user_id FROM sys_user_data_scope_target
                WHERE user_id = ? AND is_active = 1 ORDER BY target_user_id
                """, (rs, i) -> rs.getLong(1), userId);
        vo.setTargetUserIds(targets);
        return vo;
    }

    @Transactional
    public void save(UserDataScopeSaveDTO dto) {
        requireAccess();
        String mode = normalizeMode(dto.getMode());
        String operator = SecurityUtils.getCurrentUsername();
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE user_id = ? AND is_active = 1", Integer.class, dto.getUserId());
        if (exists == null || exists == 0) {
            throw new BusinessException("用户不存在");
        }
        jdbc.update("""
                INSERT INTO sys_user_data_scope (user_id, mode, create_by, is_active)
                VALUES (?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE mode = VALUES(mode), update_by = VALUES(create_by), is_active = 1
                """, dto.getUserId(), mode, operator);

        jdbc.update("UPDATE sys_user_data_scope_target SET is_active = 0 WHERE user_id = ?", dto.getUserId());
        if (MODE_PERSON.equals(mode) && dto.getTargetUserIds() != null) {
            Set<Long> ids = new LinkedHashSet<>();
            for (Long id : dto.getTargetUserIds()) {
                if (id != null) {
                    ids.add(id);
                }
            }
            for (Long targetId : ids) {
                jdbc.update("""
                        INSERT INTO sys_user_data_scope_target (user_id, target_user_id, is_active)
                        VALUES (?, ?, 1)
                        ON DUPLICATE KEY UPDATE is_active = 1
                        """, dto.getUserId(), targetId);
            }
        }
    }

    /** 供数据范围过滤：无覆盖返回 null；SELF/PERSON 返回目标人集合（可能为空表示不可见） */
    public Set<Long> resolveVisibleUserIds(Long viewerUserId) {
        if (viewerUserId == null) {
            return null;
        }
        String mode = jdbc.query("""
                SELECT mode FROM sys_user_data_scope WHERE user_id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : null, viewerUserId);
        if (!StringUtils.hasText(mode) || MODE_DEFAULT.equalsIgnoreCase(mode)) {
            return null;
        }
        if (MODE_SELF.equalsIgnoreCase(mode)) {
            return Set.of(viewerUserId);
        }
        if (MODE_PERSON.equalsIgnoreCase(mode)) {
            List<Long> targets = jdbc.query("""
                    SELECT target_user_id FROM sys_user_data_scope_target
                    WHERE user_id = ? AND is_active = 1
                    """, (rs, i) -> rs.getLong(1), viewerUserId);
            return new LinkedHashSet<>(targets == null ? List.of() : targets);
        }
        return null;
    }

    private static String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return MODE_DEFAULT;
        }
        String m = mode.trim().toUpperCase(Locale.ROOT);
        if (MODE_DEFAULT.equals(m) || MODE_PERSON.equals(m) || MODE_SELF.equals(m)) {
            return m;
        }
        throw new BusinessException("不支持的数据权限模式");
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
