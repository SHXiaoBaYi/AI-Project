package com.base.admin.service;

import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class HrDataScope {

    private final UserDataScopeService userDataScopeService;

    public void apply(StringBuilder sql, List<Object> args, String requisitionAlias, String applicationAlias) {
        LoginUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            sql.append(" AND 1 = 0 ");
            return;
        }
        Set<String> permissions = user.getPermissions();
        if (permissions.contains("*:*:*") || permissions.contains("hr:scope:all")) {
            applyPersonOverride(sql, args, user.getUserId(), requisitionAlias, applicationAlias);
            return;
        }
        if (permissions.contains("hr:scope:owner")) {
            sql.append(" AND ").append(requisitionAlias)
                    .append(".id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(user.getUserId());
            applyPersonOverride(sql, args, user.getUserId(), requisitionAlias, applicationAlias);
            return;
        }
        if (permissions.contains("hr:scope:interviewer")) {
            if (applicationAlias != null) {
                sql.append(" AND ").append(applicationAlias)
                        .append(".id IN (SELECT application_id FROM hr_interview_round WHERE interviewer_user_id = ? AND is_active = 1")
                        .append(" UNION SELECT application_id FROM hr_interview_invite WHERE interviewer_user_id = ? AND is_active = 1) ");
                args.add(user.getUserId());
                args.add(user.getUserId());
            } else {
                sql.append(" AND ").append(requisitionAlias)
                        .append(".id IN (SELECT a.requisition_id FROM hr_application a JOIN hr_interview_round rd ON rd.application_id = a.id")
                        .append(" WHERE rd.interviewer_user_id = ? AND rd.is_active = 1 AND a.is_active = 1) ");
                args.add(user.getUserId());
            }
        }
        applyPersonOverride(sql, args, user.getUserId(), requisitionAlias, applicationAlias);
    }

    /**
     * Bella 配置的「按人」覆盖：在角色范围之上再收窄为与目标人员相关的数据。
     * 相关：需求负责人 / 面试官 / 邀约面试官。
     */
    private void applyPersonOverride(StringBuilder sql, List<Object> args, Long viewerUserId,
                                     String requisitionAlias, String applicationAlias) {
        Set<Long> visible = userDataScopeService.resolveVisibleUserIds(viewerUserId);
        if (visible == null) {
            return;
        }
        if (visible.isEmpty()) {
            sql.append(" AND 1 = 0 ");
            return;
        }
        String placeholders = visible.stream().map(id -> "?").collect(Collectors.joining(","));
        String relatedRequisition = requisitionAlias + ".id IN ("
                + "SELECT requisition_id FROM hr_requisition_owner WHERE is_active = 1 AND user_id IN (" + placeholders + ")"
                + " UNION SELECT a.requisition_id FROM hr_application a"
                + " JOIN hr_interview_round rd ON rd.application_id = a.id AND rd.is_active = 1"
                + " WHERE a.is_active = 1 AND rd.interviewer_user_id IN (" + placeholders + ")"
                + " UNION SELECT a.requisition_id FROM hr_application a"
                + " JOIN hr_interview_invite inv ON inv.application_id = a.id AND inv.is_active = 1"
                + " WHERE a.is_active = 1 AND inv.interviewer_user_id IN (" + placeholders + ")"
                + ")";
        if (applicationAlias != null) {
            sql.append(" AND (").append(relatedRequisition)
                    .append(" OR ").append(applicationAlias).append(".id IN (")
                    .append("SELECT application_id FROM hr_interview_round WHERE is_active = 1 AND interviewer_user_id IN (")
                    .append(placeholders).append(")")
                    .append(" UNION SELECT application_id FROM hr_interview_invite WHERE is_active = 1 AND interviewer_user_id IN (")
                    .append(placeholders).append(")")
                    .append(")) ");
            // relatedRequisition uses visible 3 times; application uses 2 more
            for (int i = 0; i < 5; i++) {
                args.addAll(visible);
            }
        } else {
            sql.append(" AND ").append(relatedRequisition).append(' ');
            for (int i = 0; i < 3; i++) {
                args.addAll(visible);
            }
        }
    }
}
