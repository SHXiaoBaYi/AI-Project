package com.base.admin.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 招聘负责人筛选：支持单选兼容字段 + 多选列表（任一命中）。 */
public final class HrOwnerFilters {

    private HrOwnerFilters() {
    }

    public static List<Long> resolve(Long ownerUserId, List<Long> ownerUserIds) {
        Set<Long> ids = new LinkedHashSet<>();
        if (ownerUserIds != null) {
            for (Long id : ownerUserIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ownerUserId != null) {
            ids.add(ownerUserId);
        }
        return List.copyOf(ids);
    }

    public static boolean isEmpty(Long ownerUserId, List<Long> ownerUserIds) {
        return resolve(ownerUserId, ownerUserIds).isEmpty();
    }

    /** 追加「需求归属任一所选负责人」条件；requisitionAlias 如 r。 */
    public static void append(StringBuilder sql, List<Object> args, String requisitionAlias,
                              Long ownerUserId, List<Long> ownerUserIds) {
        List<Long> ids = resolve(ownerUserId, ownerUserIds);
        if (ids.isEmpty()) {
            return;
        }
        String alias = (requisitionAlias == null || requisitionAlias.isBlank()) ? "r" : requisitionAlias;
        sql.append(" AND ").append(alias)
                .append(".id IN (SELECT requisition_id FROM hr_requisition_owner WHERE is_active = 1 AND user_id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
            args.add(ids.get(i));
        }
        sql.append(")) ");
    }

    public static List<Long> asMutable(Long ownerUserId, List<Long> ownerUserIds) {
        return new ArrayList<>(resolve(ownerUserId, ownerUserIds));
    }
}
