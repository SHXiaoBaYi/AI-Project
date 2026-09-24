package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 将数据权限快照应用到业务查询（列表 / 看板 / 筛选项共用）。
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
        if (!snap.geoTopicIds().isEmpty()) {
            wrapper.in(GeoMonitorDaily::getTopicId, snap.geoTopicIds());
        }
        if (!snap.geoPlatformNames().isEmpty()) {
            wrapper.in(GeoMonitorDaily::getPlatform, snap.geoPlatformNames());
        }
        if (snap.geoSelfOwnerOnly()) {
            if (uid == null) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.eq(GeoMonitorDaily::getOwnerUserId, uid);
            }
        }
    }

    public void applyGeoPlacement(LambdaQueryWrapper<GeoContentPlacement> wrapper) {
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.geoEnabled()) {
            return;
        }
        Long uid = SecurityUtils.getCurrentUserId();
        if (!snap.geoTopicIds().isEmpty()) {
            wrapper.in(GeoContentPlacement::getTopicId, snap.geoTopicIds());
        }
        if (snap.geoSelfWriterOnly() || snap.geoSelfPublisherOnly()) {
            if (uid == null) {
                wrapper.apply("1 = 0");
                return;
            }
            if (snap.geoSelfWriterOnly() && snap.geoSelfPublisherOnly()) {
                wrapper.and(w -> w.eq(GeoContentPlacement::getOwnerUserId, uid)
                        .or()
                        .eq(GeoContentPlacement::getPublisherUserId, uid));
            } else if (snap.geoSelfWriterOnly()) {
                wrapper.eq(GeoContentPlacement::getOwnerUserId, uid);
            } else {
                wrapper.eq(GeoContentPlacement::getPublisherUserId, uid);
            }
        }
        if (!snap.geoPlatformNames().isEmpty()) {
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
            wrapper.apply(
                    "EXISTS (SELECT 1 FROM geo_content_placement_item i WHERE i.placement_id = geo_content_placement.id "
                            + "AND i.is_active = 1 AND i.platform_name IN (" + ph + "))",
                    args.toArray());
        }
    }

    public void applyTask(LambdaQueryWrapper<SysTask> wrapper) {
        UserDataScopeSnapshot snap = snapshot();
        if (snap.globalAll() || !snap.taskEnabled()) {
            return;
        }
        Long uid = SecurityUtils.getCurrentUserId();
        if (!snap.taskTypeNames().isEmpty()) {
            wrapper.in(SysTask::getTaskType, snap.taskTypeNames());
        }
        if (snap.taskOwnerOnly() || snap.taskAssigneeOnly()) {
            if (uid == null) {
                wrapper.apply("1 = 0");
                return;
            }
            if (snap.taskOwnerOnly() && snap.taskAssigneeOnly()) {
                wrapper.and(w -> w.eq(SysTask::getOwnerUserId, uid)
                        .or()
                        .apply("EXISTS (SELECT 1 FROM sys_task_assignee a WHERE a.task_id = sys_task.id AND a.user_id = {0} AND a.is_active = 1)",
                                uid));
            } else if (snap.taskOwnerOnly()) {
                wrapper.eq(SysTask::getOwnerUserId, uid);
            } else {
                wrapper.apply(
                        "EXISTS (SELECT 1 FROM sys_task_assignee a WHERE a.task_id = sys_task.id AND a.user_id = {0} AND a.is_active = 1)",
                        uid);
            }
        }
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
