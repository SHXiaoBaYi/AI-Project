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
        UserDataScopeSnapshot snap = userDataScopeService.resolveSnapshot(user.getUserId());
        if (snap.globalAll()) {
            return;
        }
        Set<String> permissions = user.getPermissions();
        if (permissions.contains("*:*:*") || permissions.contains("hr:scope:all")) {
            applyDeptOverride(sql, args, snap, requisitionAlias);
            applyPersonOverride(sql, args, snap, requisitionAlias, applicationAlias);
            return;
        }
        if (permissions.contains("hr:scope:owner")) {
            sql.append(" AND ").append(requisitionAlias)
                    .append(".id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(user.getUserId());
            applyDeptOverride(sql, args, snap, requisitionAlias);
            applyPersonOverride(sql, args, snap, requisitionAlias, applicationAlias);
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
        applyDeptOverride(sql, args, snap, requisitionAlias);
        applyPersonOverride(sql, args, snap, requisitionAlias, applicationAlias);
    }

    private void applyDeptOverride(StringBuilder sql, List<Object> args, UserDataScopeSnapshot snap, String requisitionAlias) {
        if (!snap.hrEnabled() || snap.hrDeptIds() == null || snap.hrDeptIds().isEmpty()) {
            return;
        }
        String placeholders = snap.hrDeptIds().stream().map(id -> "?").collect(Collectors.joining(","));
        sql.append(" AND ").append(requisitionAlias).append(".dept_id IN (").append(placeholders).append(") ");
        args.addAll(snap.hrDeptIds());
    }

    private void applyPersonOverride(StringBuilder sql, List<Object> args, UserDataScopeSnapshot snap,
                                     String requisitionAlias, String applicationAlias) {
        if (!snap.hrEnabled()) {
            return;
        }
        Set<Long> visible = snap.hrVisibleUserIds();
        if (visible == null) {
            // DEFAULT person mode
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
