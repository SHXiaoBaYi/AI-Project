package com.base.admin.service;

import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class HrDataScope {

    public void apply(StringBuilder sql, List<Object> args, String requisitionAlias, String applicationAlias) {
        LoginUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            sql.append(" AND 1 = 0 ");
            return;
        }
        Set<String> permissions = user.getPermissions();
        if (permissions.contains("*:*:*") || permissions.contains("hr:scope:all")) {
            return;
        }
        if (permissions.contains("hr:scope:owner")) {
            sql.append(" AND ").append(requisitionAlias)
                    .append(".id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(user.getUserId());
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
    }
}
