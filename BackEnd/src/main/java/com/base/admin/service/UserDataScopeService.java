package com.base.admin.service;

import com.base.admin.domain.dto.UserDataScopeSaveDTO;
import com.base.admin.domain.vo.UserDataScopeGeoVO;
import com.base.admin.domain.vo.UserDataScopeHrVO;
import com.base.admin.domain.vo.UserDataScopeMetaVO;
import com.base.admin.domain.vo.UserDataScopeTaskVO;
import com.base.admin.domain.vo.UserDataScopeVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.LocalhostAccess;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
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
    private final ObjectMapper objectMapper;

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

    public UserDataScopeMetaVO meta() {
        requireAccess();
        UserDataScopeMetaVO meta = new UserDataScopeMetaVO();
        meta.setTopics(jdbc.query("""
                SELECT id, topic_name FROM geo_topic WHERE is_active = 1 ORDER BY id
                """, (rs, i) -> {
            UserDataScopeMetaVO.Option o = new UserDataScopeMetaVO.Option();
            o.setValue(rs.getLong("id"));
            o.setLabel(rs.getString("topic_name"));
            return o;
        }));
        meta.setPlatforms(jdbc.query("""
                SELECT id, platform_name, platform_type FROM geo_platform WHERE is_active = 1
                ORDER BY platform_type, sort_order, id
                """, (rs, i) -> {
            UserDataScopeMetaVO.Option o = new UserDataScopeMetaVO.Option();
            o.setValue(rs.getLong("id"));
            o.setLabel(rs.getString("platform_name"));
            o.setExtra(rs.getString("platform_type"));
            return o;
        }));
        meta.setDepartments(jdbc.query("""
                SELECT id, name, ancestors FROM hr_department WHERE is_active = 1 ORDER BY sort_order, id
                """, (rs, i) -> {
            UserDataScopeMetaVO.Option o = new UserDataScopeMetaVO.Option();
            o.setValue(rs.getLong("id"));
            o.setLabel(rs.getString("name"));
            o.setExtra(rs.getString("ancestors"));
            return o;
        }));
        meta.setTaskTypes(jdbc.query("""
                SELECT id, type_name FROM sys_task_type WHERE is_active = 1 ORDER BY sort_order, id
                """, (rs, i) -> {
            UserDataScopeMetaVO.Option o = new UserDataScopeMetaVO.Option();
            o.setValue(rs.getLong("id"));
            o.setLabel(rs.getString("type_name"));
            return o;
        }));
        return meta;
    }

    public List<UserDataScopeVO> listUsers() {
        requireAccess();
        return jdbc.query("""
                SELECT u.user_id, u.username, u.nickname,
                       COALESCE(s.mode, 'DEFAULT') AS mode,
                       COALESCE(s.global_all, 0) AS global_all,
                       s.geo_config, s.hr_config, s.task_config
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
            vo.setGlobalAll(rs.getInt("global_all") == 1);
            vo.setGeo(readJson(rs.getString("geo_config"), UserDataScopeGeoVO.class, new UserDataScopeGeoVO()));
            vo.setHr(readJson(rs.getString("hr_config"), UserDataScopeHrVO.class, new UserDataScopeHrVO()));
            vo.setTask(readJson(rs.getString("task_config"), UserDataScopeTaskVO.class, new UserDataScopeTaskVO()));
            syncLegacyFromHr(vo);
            vo.setSummary(buildSummary(vo));
            return vo;
        });
    }

    public UserDataScopeVO get(Long userId) {
        requireAccess();
        if (userId == null) {
            throw new BusinessException("请选择用户");
        }
        UserDataScopeVO vo = jdbc.query("""
                SELECT u.user_id, u.username, u.nickname,
                       COALESCE(s.mode, 'DEFAULT') AS mode,
                       COALESCE(s.global_all, 0) AS global_all,
                       s.geo_config, s.hr_config, s.task_config
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
            row.setGlobalAll(rs.getInt("global_all") == 1);
            row.setGeo(readJson(rs.getString("geo_config"), UserDataScopeGeoVO.class, new UserDataScopeGeoVO()));
            row.setHr(readJson(rs.getString("hr_config"), UserDataScopeHrVO.class, new UserDataScopeHrVO()));
            row.setTask(readJson(rs.getString("task_config"), UserDataScopeTaskVO.class, new UserDataScopeTaskVO()));
            return row;
        }, userId);
        if (vo == null) {
            throw new BusinessException("用户不存在");
        }
        List<Long> targets = jdbc.query("""
                SELECT target_user_id FROM sys_user_data_scope_target
                WHERE user_id = ? AND is_active = 1 ORDER BY target_user_id
                """, (rs, i) -> rs.getLong(1), userId);
        if (vo.getHr() == null) {
            vo.setHr(new UserDataScopeHrVO());
        }
        if ((vo.getHr().getTargetUserIds() == null || vo.getHr().getTargetUserIds().isEmpty()) && targets != null) {
            vo.getHr().setTargetUserIds(new ArrayList<>(targets));
        }
        if (!StringUtils.hasText(vo.getHr().getPersonMode())) {
            vo.getHr().setPersonMode(StringUtils.hasText(vo.getMode()) ? vo.getMode() : MODE_DEFAULT);
        }
        syncLegacyFromHr(vo);
        vo.setSummary(buildSummary(vo));
        return vo;
    }

    @Transactional
    public void save(UserDataScopeSaveDTO dto) {
        requireAccess();
        String operator = SecurityUtils.getCurrentUsername();
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE user_id = ? AND is_active = 1", Integer.class, dto.getUserId());
        if (exists == null || exists == 0) {
            throw new BusinessException("用户不存在");
        }

        boolean globalAll = Boolean.TRUE.equals(dto.getGlobalAll());
        UserDataScopeGeoVO geo = dto.getGeo() != null ? dto.getGeo() : new UserDataScopeGeoVO();
        UserDataScopeHrVO hr = dto.getHr() != null ? dto.getHr() : new UserDataScopeHrVO();
        UserDataScopeTaskVO task = dto.getTask() != null ? dto.getTask() : new UserDataScopeTaskVO();
        if (globalAll) {
            geo = new UserDataScopeGeoVO();
            hr = new UserDataScopeHrVO();
            task = new UserDataScopeTaskVO();
        }
        String personMode = normalizeMode(hr.getPersonMode());
        hr.setPersonMode(personMode);
        List<Long> targetIds = MODE_PERSON.equals(personMode) ? cleanIds(hr.getTargetUserIds()) : List.of();
        hr.setTargetUserIds(new ArrayList<>(targetIds));
        geo.setTopicIds(cleanIds(geo.getTopicIds()));
        geo.setPlatformIds(cleanIds(geo.getPlatformIds()));
        hr.setDeptIds(cleanIds(hr.getDeptIds()));
        task.setTaskTypeIds(cleanIds(task.getTaskTypeIds()));

        jdbc.update("""
                INSERT INTO sys_user_data_scope
                  (user_id, mode, global_all, geo_config, hr_config, task_config, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                  mode = VALUES(mode),
                  global_all = VALUES(global_all),
                  geo_config = VALUES(geo_config),
                  hr_config = VALUES(hr_config),
                  task_config = VALUES(task_config),
                  update_by = VALUES(create_by),
                  is_active = 1
                """, dto.getUserId(), personMode, globalAll ? 1 : 0,
                writeJson(geo), writeJson(hr), writeJson(task), operator);

        jdbc.update("UPDATE sys_user_data_scope_target SET is_active = 0 WHERE user_id = ?", dto.getUserId());
        if (!globalAll && Boolean.TRUE.equals(hr.getEnabled()) && MODE_PERSON.equals(personMode)) {
            for (Long targetId : targetIds) {
                jdbc.update("""
                        INSERT INTO sys_user_data_scope_target (user_id, target_user_id, is_active)
                        VALUES (?, ?, 1)
                        ON DUPLICATE KEY UPDATE is_active = 1
                        """, dto.getUserId(), targetId);
            }
        }
    }

    /** 供招聘数据范围过滤：无覆盖返回 null；SELF/PERSON 返回目标人集合 */
    public Set<Long> resolveVisibleUserIds(Long viewerUserId) {
        if (viewerUserId == null) {
            return null;
        }
        return jdbc.query("""
                SELECT mode, global_all, hr_config FROM sys_user_data_scope
                WHERE user_id = ? AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            if (rs.getInt("global_all") == 1) {
                return null;
            }
            UserDataScopeHrVO hr = readJson(rs.getString("hr_config"), UserDataScopeHrVO.class, null);
            String mode = rs.getString("mode");
            if (hr != null && Boolean.TRUE.equals(hr.getEnabled()) && StringUtils.hasText(hr.getPersonMode())) {
                mode = hr.getPersonMode();
            }
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
                        """, (r, i) -> r.getLong(1), viewerUserId);
                return new LinkedHashSet<>(targets == null ? List.of() : targets);
            }
            return null;
        }, viewerUserId);
    }

    private static void syncLegacyFromHr(UserDataScopeVO vo) {
        if (vo.getHr() != null) {
            vo.setMode(StringUtils.hasText(vo.getHr().getPersonMode()) ? vo.getHr().getPersonMode() : MODE_DEFAULT);
            vo.setTargetUserIds(vo.getHr().getTargetUserIds() != null
                    ? new ArrayList<>(vo.getHr().getTargetUserIds()) : new ArrayList<>());
        }
    }

    private static String buildSummary(UserDataScopeVO vo) {
        if (Boolean.TRUE.equals(vo.getGlobalAll())) {
            return "全量业务";
        }
        List<String> parts = new ArrayList<>();
        if (vo.getGeo() != null && Boolean.TRUE.equals(vo.getGeo().getEnabled())) {
            parts.add("GEO");
        }
        if (vo.getHr() != null && Boolean.TRUE.equals(vo.getHr().getEnabled())) {
            parts.add("招聘");
        }
        if (vo.getTask() != null && Boolean.TRUE.equals(vo.getTask().getEnabled())) {
            parts.add("任务");
        }
        if (parts.isEmpty()) {
            return "默认";
        }
        return String.join("+", parts);
    }

    private static List<Long> cleanIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                set.add(id);
            }
        }
        return new ArrayList<>(set);
    }

    private static String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return MODE_DEFAULT;
        }
        String m = mode.trim().toUpperCase(Locale.ROOT);
        if (MODE_DEFAULT.equals(m) || MODE_PERSON.equals(m) || MODE_SELF.equals(m)) {
            return m;
        }
        throw new BusinessException("不支持的人员可见模式");
    }

    private <T> T readJson(String json, Class<T> type, T fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            T value = objectMapper.readValue(json, type);
            return value != null ? value : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("数据权限配置序列化失败");
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
