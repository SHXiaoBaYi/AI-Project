package com.base.admin.service;

import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 招聘数据权限。切片开启时，默认额外可见 create_by = 当前用户名 的需求/候选人。
 */
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

        StringBuilder scoped = new StringBuilder();
        List<Object> scopedArgs = new ArrayList<>();
        Set<String> permissions = user.getPermissions();

        if (permissions.contains("*:*:*") || permissions.contains("hr:scope:all")) {
            appendDeptOverride(scoped, scopedArgs, snap, requisitionAlias);
            appendPersonOverride(scoped, scopedArgs, snap, requisitionAlias, applicationAlias);
        } else if (permissions.contains("hr:scope:owner")) {
            scoped.append(" AND ").append(requisitionAlias)
                    .append(".id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            scopedArgs.add(user.getUserId());
            appendDeptOverride(scoped, scopedArgs, snap, requisitionAlias);
            appendPersonOverride(scoped, scopedArgs, snap, requisitionAlias, applicationAlias);
        } else if (permissions.contains("hr:scope:interviewer")) {
            if (applicationAlias != null) {
                scoped.append(" AND ").append(applicationAlias)
                        .append(".id IN (SELECT application_id FROM hr_interview_round WHERE interviewer_user_id = ? AND is_active = 1")
                        .append(" UNION SELECT application_id FROM hr_interview_invite WHERE interviewer_user_id = ? AND is_active = 1) ");
                scopedArgs.add(user.getUserId());
                scopedArgs.add(user.getUserId());
            } else {
                scoped.append(" AND ").append(requisitionAlias)
                        .append(".id IN (SELECT a.requisition_id FROM hr_application a JOIN hr_interview_round rd ON rd.application_id = a.id")
                        .append(" WHERE rd.interviewer_user_id = ? AND rd.is_active = 1 AND a.is_active = 1) ");
                scopedArgs.add(user.getUserId());
            }
            appendDeptOverride(scoped, scopedArgs, snap, requisitionAlias);
            appendPersonOverride(scoped, scopedArgs, snap, requisitionAlias, applicationAlias);
        } else {
            appendDeptOverride(scoped, scopedArgs, snap, requisitionAlias);
            appendPersonOverride(scoped, scopedArgs, snap, requisitionAlias, applicationAlias);
        }

        if (scoped.isEmpty()) {
            return;
        }

        String scopedBody = stripLeadingAnd(scoped.toString());
        String username = user.getUsername();
        boolean creatorOr = snap.hrEnabled() && StringUtils.hasText(username);

        if (creatorOr) {
            sql.append(" AND ((").append(scopedBody).append(") OR ")
                    .append(requisitionAlias).append(".create_by = ?");
            args.addAll(scopedArgs);
            args.add(username);
            if (applicationAlias != null) {
                sql.append(" OR ").append(applicationAlias).append(".create_by = ?");
                args.add(username);
            }
            sql.append(") ");
        } else {
            sql.append(" AND (").append(scopedBody).append(") ");
            args.addAll(scopedArgs);
        }
    }

    private void appendDeptOverride(StringBuilder sql, List<Object> args, UserDataScopeSnapshot snap, String requisitionAlias) {
        if (!snap.hrEnabled() || snap.hrDeptIds() == null || snap.hrDeptIds().isEmpty()) {
            return;
        }
        String placeholders = snap.hrDeptIds().stream().map(id -> "?").collect(Collectors.joining(","));
        sql.append(" AND ").append(requisitionAlias).append(".dept_id IN (").append(placeholders).append(") ");
        args.addAll(snap.hrDeptIds());
    }

    private void appendPersonOverride(StringBuilder sql, List<Object> args, UserDataScopeSnapshot snap,
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

    private static String stripLeadingAnd(String sql) {
        String t = sql.trim();
        if (t.regionMatches(true, 0, "AND ", 0, 4)) {
            return t.substring(4).trim();
        }
        return t;
    }
}
