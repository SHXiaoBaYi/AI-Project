package com.base.admin.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前登录用户解析后的数据权限快照。
 */
public record UserDataScopeSnapshot(
        boolean globalAll,
        boolean geoEnabled,
        Set<Long> geoTopicIds,
        Set<String> geoPlatformNames,
        boolean geoSelfOwnerOnly,
        boolean geoSelfWriterOnly,
        boolean geoSelfPublisherOnly,
        boolean hrEnabled,
        Set<Long> hrDeptIds,
        String hrPersonMode,
        Set<Long> hrVisibleUserIds,
        boolean taskEnabled,
        Set<String> taskTypeNames,
        boolean taskOwnerOnly,
        boolean taskAssigneeOnly
) {
    /** 全局全量：不追加任何切片过滤。 */
    public static UserDataScopeSnapshot unrestricted() {
        return new UserDataScopeSnapshot(
                true,
                false, Set.of(), Set.of(), false, false, false,
                false, Set.of(), "DEFAULT", null,
                false, Set.of(), false, false);
    }

    /** 未配置：各模块不额外收窄。 */
    public static UserDataScopeSnapshot defaults() {
        return new UserDataScopeSnapshot(
                false,
                false, Set.of(), Set.of(), false, false, false,
                false, Set.of(), "DEFAULT", null,
                false, Set.of(), false, false);
    }

    public boolean denyAllHrPerson() {
        return hrEnabled && hrVisibleUserIds != null && hrVisibleUserIds.isEmpty();
    }

    public static Set<Long> copyLongs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                set.add(id);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    public static Set<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                set.add(value.trim());
            }
        }
        return Collections.unmodifiableSet(set);
    }
}
