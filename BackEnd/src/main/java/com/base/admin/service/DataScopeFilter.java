package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 将数据权限快照应用到业务查询（列表 / 看板 / 筛选项共用）。
 * 任务 / GEO / 招聘切片开启时，默认额外可见「创建人 = 当前用户」的数据。
 */
@Component
@RequiredArgsConstructor
public class DataScopeFilter {

    private final UserDataScopeService userDataScopeService;

    public UserDataScopeSnapshot snapshot() {
        return userDataScopeService.currentSnapshot();
    }

    public void applyGeoDaily(LambdaQueryWrapper<GeoMonitorDaily> wrapper) {
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.geoEnabled()) {
            return;
        }
        Long uid = SecurityUtils.getCurrentUserId();
        String username = creatorUsername();
        boolean hasTopic = !snap.geoTopicIds().isEmpty();
        boolean hasPlatform = !snap.geoPlatformNames().isEmpty();
        boolean selfOwner = snap.geoSelfOwnerOnly();
        if (!hasTopic && !hasPlatform && !selfOwner) {
            return;
        }
        wrapper.and(outer -> {
            outer.and(inner -> {
                if (hasTopic) {
                    inner.in(GeoMonitorDaily::getTopicId, snap.geoTopicIds());
                }
                if (hasPlatform) {
                    inner.in(GeoMonitorDaily::getPlatform, snap.geoPlatformNames());
                }
                if (selfOwner) {
                    if (uid == null) {
                        inner.apply("1 = 0");
                    } else {
                        inner.eq(GeoMonitorDaily::getOwnerUserId, uid);
                    }
                }
            });
            if (StringUtils.hasText(username)) {
                outer.or().eq(GeoMonitorDaily::getCreateBy, username);
            }
        });
    }

    public void applyGeoPlacement(LambdaQueryWrapper<GeoContentPlacement> wrapper) {
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.geoEnabled()) {
            return;
        }
        Long uid = SecurityUtils.getCurrentUserId();
        String username = creatorUsername();
        boolean hasTopic = !snap.geoTopicIds().isEmpty();
        boolean selfWriter = snap.geoSelfWriterOnly();
        boolean selfPublisher = snap.geoSelfPublisherOnly();
        boolean hasPlatform = !snap.geoPlatformNames().isEmpty();
        if (!hasTopic && !selfWriter && !selfPublisher && !hasPlatform) {
            return;
        }
        wrapper.and(outer -> {
            outer.and(inner -> {
                if (hasTopic) {
                    inner.in(GeoContentPlacement::getTopicId, snap.geoTopicIds());
                }
                if (selfWriter || selfPublisher) {
                    if (uid == null) {
                        inner.apply("1 = 0");
                    } else if (selfWriter && selfPublisher) {
                        inner.and(w -> w.eq(GeoContentPlacement::getOwnerUserId, uid)
                                .or()
                                .eq(GeoContentPlacement::getPublisherUserId, uid));
                    } else if (selfWriter) {
                        inner.eq(GeoContentPlacement::getOwnerUserId, uid);
                    } else {
                        inner.eq(GeoContentPlacement::getPublisherUserId, uid);
                    }
                }
                if (hasPlatform) {
                    int i = 0;
                    StringBuilder ph = new StringBuilder();
                    List<Object> args = new ArrayList<>();
                    for (String name : snap.geoPlatformNames()) {
                        if (!ph.isEmpty()) {
                            ph.append(',');
                        }
                        ph.append('{').append(i++).append('}');
                        args.add(name);
                    }
                    inner.apply(
                            "EXISTS (SELECT 1 FROM geo_content_placement_item i WHERE i.placement_id = geo_content_placement.id "
                                    + "AND i.is_active = 1 AND i.platform_name IN (" + ph + "))",
                            args.toArray());
                }
            });
            if (StringUtils.hasText(username)) {
                outer.or().eq(GeoContentPlacement::getCreateBy, username);
            }
        });
    }

    public void applyTask(LambdaQueryWrapper<SysTask> wrapper) {
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.taskEnabled()) {
            return;
        }
        Long uid = SecurityUtils.getCurrentUserId();
        boolean hasType = !snap.taskTypeNames().isEmpty();
        boolean ownerOnly = snap.taskOwnerOnly();
        boolean assigneeOnly = snap.taskAssigneeOnly();
        if (!hasType && !ownerOnly && !assigneeOnly) {
            return;
        }
        wrapper.and(outer -> {
            outer.and(inner -> {
                if (hasType) {
                    inner.in(SysTask::getTaskType, snap.taskTypeNames());
                }
                if (ownerOnly || assigneeOnly) {
                    if (uid == null) {
                        inner.apply("1 = 0");
                    } else if (ownerOnly && assigneeOnly) {
                        inner.and(w -> w.eq(SysTask::getOwnerUserId, uid)
                                .or()
                                .apply("EXISTS (SELECT 1 FROM sys_task_assignee a WHERE a.task_id = sys_task.id AND a.user_id = {0} AND a.is_active = 1)",
                                        uid));
                    } else if (ownerOnly) {
                        inner.eq(SysTask::getOwnerUserId, uid);
                    } else {
                        inner.apply(
                                "EXISTS (SELECT 1 FROM sys_task_assignee a WHERE a.task_id = sys_task.id AND a.user_id = {0} AND a.is_active = 1)",
                                uid);
                    }
                }
            });
            if (uid != null) {
                outer.or().eq(SysTask::getCreatorUserId, uid);
            }
        });
    }

    /** 筛选项：话题下拉。 */
    public <T> List<T> filterGeoTopics(List<T> list, Function<T, Long> idGetter) {
        if (list == null || list.isEmpty()) {
            return list == null ? List.of() : list;
        }
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.geoEnabled() || snap.geoTopicIds().isEmpty()) {
            return list;
        }
        Set<Long> allowed = snap.geoTopicIds();
        return list.stream().filter(item -> allowed.contains(idGetter.apply(item))).toList();
    }

    /** 筛选项：平台下拉。 */
    public <T> List<T> filterGeoPlatforms(List<T> list, Function<T, String> nameGetter) {
        if (list == null || list.isEmpty()) {
            return list == null ? List.of() : list;
        }
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.geoEnabled() || snap.geoPlatformNames().isEmpty()) {
            return list;
        }
        Set<String> allowed = snap.geoPlatformNames();
        return list.stream().filter(item -> {
            String name = nameGetter.apply(item);
            return name != null && allowed.contains(name);
        }).toList();
    }

    /** 筛选项：GEO 负责人（仅本人时只返回当前用户）。 */
    public <T> List<T> filterGeoOwners(List<T> list, Function<T, Long> userIdGetter) {
        if (list == null || list.isEmpty()) {
            return list == null ? List.of() : list;
        }
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.geoEnabled() || !snap.geoSelfOwnerOnly()) {
            return list;
        }
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) {
            return List.of();
        }
        return list.stream().filter(item -> Objects.equals(uid, userIdGetter.apply(item))).toList();
    }

    /** 筛选项：任务类型。 */
    public <T> List<T> filterTaskTypes(List<T> list, Function<T, String> nameGetter) {
        if (list == null || list.isEmpty()) {
            return list == null ? List.of() : list;
        }
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.taskEnabled() || snap.taskTypeNames().isEmpty()) {
            return list;
        }
        Set<String> allowed = snap.taskTypeNames();
        return list.stream().filter(item -> {
            String name = nameGetter.apply(item);
            return name != null && allowed.contains(name);
        }).toList();
    }

    /**
     * 筛选项：部门扁平列表（再交给 buildTree）。
     * 仅保留授权部门；父节点不在范围内时挂到根，避免树断裂。
     */
    public List<Map<String, Object>> filterHrDepartmentsFlat(List<Map<String, Object>> flat) {
        if (flat == null || flat.isEmpty()) {
            return flat == null ? List.of() : flat;
        }
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.hrEnabled() || snap.hrDeptIds() == null || snap.hrDeptIds().isEmpty()) {
            return flat;
        }
        Set<Long> allowed = snap.hrDeptIds();
        List<Map<String, Object>> kept = flat.stream()
                .filter(row -> allowed.contains(toLong(row.get("id"))))
                .collect(Collectors.toCollection(ArrayList::new));
        Set<Long> keptIds = kept.stream().map(row -> toLong(row.get("id"))).filter(Objects::nonNull).collect(Collectors.toSet());
        for (Map<String, Object> row : kept) {
            Long parentId = toLong(row.get("parent_id"));
            if (parentId != null && parentId != 0L && !keptIds.contains(parentId)) {
                row.put("parent_id", 0L);
            }
        }
        return kept;
    }

    /** 筛选项：HR 用户下拉（人员模式为 SELF / PERSON 时收窄）。 */
    public List<Map<String, Object>> filterHrUsers(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return list == null ? List.of() : list;
        }
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.hrEnabled()) {
            return list;
        }
        Set<Long> visible = snap.hrVisibleUserIds();
        if (visible == null) {
            return list;
        }
        if (visible.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(row -> visible.contains(toLong(row.get("user_id"))))
                .toList();
    }

    private static String creatorUsername() {
        String username = SecurityUtils.getCurrentUsername();
        if (!StringUtils.hasText(username) || "system".equals(username)) {
            return null;
        }
        return username;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
