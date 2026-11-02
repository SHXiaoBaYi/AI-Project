package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.HrRequisitionLogQueryDTO;
import com.base.admin.domain.vo.HrRequisitionLogVO;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 招聘需求 CUD 操作日志：谁、何时、做了什么、原值/新值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrRequisitionLogService {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_STATUS = "STATUS";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public void write(Long requisitionId, String jobName, String action, String summary,
                      Map<String, Object> before, Map<String, Object> after) {
        Operator op = currentOperator();
        try {
            jdbc.update("""
                    INSERT INTO hr_requisition_log
                      (requisition_id, job_name, action, summary, before_json, after_json,
                       operator_user_id, operator_username, operator_nickname, operated_at, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,NOW(),1)
                    """,
                    requisitionId,
                    jobName == null ? "" : jobName,
                    action,
                    summary == null ? "" : truncate(summary, 1000),
                    toJson(before),
                    toJson(after),
                    op.userId(),
                    op.username(),
                    op.nickname());
        } catch (Exception e) {
            log.warn("招聘需求操作日志写入失败 action={} reqId={}: {}", action, requisitionId, e.getMessage());
        }
    }

    public Map<String, Object> loadSnapshot(Long requisitionId) {
        if (requisitionId == null) {
            return Map.of();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, job_name, job_desc, salary_range, education_req, experience_req, skill_req,
                       task_requirement, status, location_code, dept_id, headcount, target_text, priority,
                       job_priority_code, work_address_id, importance_level, difficulty_level,
                       received_date, onboard_date, is_active
                FROM hr_requisition WHERE id = ?
                """, requisitionId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> snap = new LinkedHashMap<>();
        Map<String, Object> row = rows.get(0);
        snap.put("id", row.get("id"));
        snap.put("jobName", row.get("job_name"));
        snap.put("jobDesc", row.get("job_desc"));
        snap.put("salaryRange", row.get("salary_range"));
        snap.put("educationReq", row.get("education_req"));
        snap.put("experienceReq", row.get("experience_req"));
        snap.put("skillReq", row.get("skill_req"));
        snap.put("taskRequirement", row.get("task_requirement"));
        snap.put("status", row.get("status"));
        snap.put("locationCode", row.get("location_code"));
        snap.put("deptId", row.get("dept_id"));
        snap.put("headcount", row.get("headcount"));
        snap.put("targetText", row.get("target_text"));
        snap.put("priority", row.get("priority"));
        snap.put("jobPriorityCode", row.get("job_priority_code"));
        snap.put("workAddressId", row.get("work_address_id"));
        snap.put("importanceLevel", row.get("importance_level"));
        snap.put("difficultyLevel", row.get("difficulty_level"));
        snap.put("receivedDate", stringify(row.get("received_date")));
        snap.put("onboardDate", stringify(row.get("onboard_date")));
        snap.put("isActive", row.get("is_active"));

        List<Long> owners = jdbc.query("""
                SELECT user_id FROM hr_requisition_owner
                WHERE requisition_id = ? AND is_active = 1 AND user_id IS NOT NULL
                ORDER BY sort_no
                """, (rs, i) -> rs.getLong(1), requisitionId);
        snap.put("ownerUserIds", owners);

        List<Map<String, Object>> rounds = jdbc.queryForList("""
                SELECT rr.round_no,
                       (SELECT GROUP_CONCAT(i.interviewer_user_id ORDER BY i.id)
                        FROM hr_requisition_round_interviewer i
                        WHERE i.requisition_id = rr.requisition_id AND i.round_no = rr.round_no AND i.is_active = 1) interviewers,
                       (SELECT GROUP_CONCAT(c.cc_user_id ORDER BY c.id)
                        FROM hr_requisition_round_cc c
                        WHERE c.requisition_id = rr.requisition_id AND c.round_no = rr.round_no AND c.is_active = 1) ccs
                FROM hr_requisition_round rr
                WHERE rr.requisition_id = ? AND rr.is_active = 1
                ORDER BY rr.round_no
                """, requisitionId);
        List<Map<String, Object>> roundSnaps = new ArrayList<>();
        for (Map<String, Object> r : rounds) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("roundNo", r.get("round_no"));
            one.put("interviewerUserIds", splitIds(r.get("interviewers")));
            one.put("ccUserIds", splitIds(r.get("ccs")));
            roundSnaps.add(one);
        }
        snap.put("interviewRounds", roundSnaps);
        return snap;
    }

    public String diffSummary(Map<String, Object> before, Map<String, Object> after) {
        if (before == null || before.isEmpty()) {
            return "新建招聘需求";
        }
        if (after == null || after.isEmpty()) {
            return "删除招聘需求";
        }
        List<String> parts = new ArrayList<>();
        for (String key : FIELD_LABELS.keySet()) {
            Object b = before.get(key);
            Object a = after.get(key);
            if (!Objects.equals(normalize(b), normalize(a))) {
                parts.add(FIELD_LABELS.get(key) + "：" + display(b) + " → " + display(a));
            }
        }
        // owners / rounds
        if (!Objects.equals(normalize(before.get("ownerUserIds")), normalize(after.get("ownerUserIds")))) {
            parts.add("负责人：" + display(before.get("ownerUserIds")) + " → " + display(after.get("ownerUserIds")));
        }
        if (!Objects.equals(normalize(before.get("interviewRounds")), normalize(after.get("interviewRounds")))) {
            parts.add("面试流程已调整");
        }
        if (parts.isEmpty()) {
            return "保存招聘需求（字段无变化）";
        }
        String joined = String.join("；", parts);
        return truncate(joined, 1000);
    }

    public PageResult<HrRequisitionLogVO> page(HrRequisitionLogQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        StringBuilder where = new StringBuilder(" WHERE is_active = 1 ");
        List<Object> args = new ArrayList<>();
        if (query.getRequisitionId() != null) {
            where.append(" AND requisition_id = ? ");
            args.add(query.getRequisitionId());
        }
        if (StringUtils.hasText(query.getAction())) {
            where.append(" AND action = ? ");
            args.add(query.getAction().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String like = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (job_name LIKE ? OR operator_nickname LIKE ? OR operator_username LIKE ? OR summary LIKE ?) ");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (query.getCreateTimeStart() != null) {
            where.append(" AND operated_at >= ? ");
            args.add(query.getCreateTimeStart().atStartOfDay());
        }
        if (query.getCreateTimeEnd() != null) {
            where.append(" AND operated_at < ? ");
            args.add(query.getCreateTimeEnd().plusDays(1).atStartOfDay());
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(1) FROM hr_requisition_log" + where, Integer.class, args.toArray());
        int t = total == null ? 0 : total;
        int offset = (pageNum - 1) * pageSize;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add(offset);
        List<HrRequisitionLogVO> rows = jdbc.query("""
                SELECT id, requisition_id, job_name, action, summary, before_json, after_json,
                       operator_user_id, operator_username, operator_nickname, operated_at
                FROM hr_requisition_log
                """ + where + " ORDER BY operated_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    HrRequisitionLogVO vo = new HrRequisitionLogVO();
                    vo.setId(rs.getLong("id"));
                    long reqId = rs.getLong("requisition_id");
                    vo.setRequisitionId(rs.wasNull() ? null : reqId);
                    vo.setJobName(rs.getString("job_name"));
                    String action = rs.getString("action");
                    vo.setAction(action);
                    vo.setActionLabel(actionLabel(action));
                    vo.setSummary(rs.getString("summary"));
                    vo.setBeforeJson(rs.getString("before_json"));
                    vo.setAfterJson(rs.getString("after_json"));
                    long uid = rs.getLong("operator_user_id");
                    vo.setOperatorUserId(rs.wasNull() ? null : uid);
                    vo.setOperatorUsername(rs.getString("operator_username"));
                    vo.setOperatorNickname(rs.getString("operator_nickname"));
                    vo.setOperatedAt(rs.getString("operated_at"));
                    return vo;
                }, listArgs.toArray());
        return new PageResult<>(t, rows);
    }

    private static String actionLabel(String action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case ACTION_CREATE -> "新增";
            case ACTION_UPDATE -> "修改";
            case ACTION_DELETE -> "删除";
            case ACTION_STATUS -> "改状态";
            default -> action;
        };
    }

    private Operator currentOperator() {
        LoginUser user = SecurityUtils.getCurrentUser();
        Long userId = user == null ? null : user.getUserId();
        String username = SecurityUtils.getCurrentUsername();
        String nickname = username;
        if (userId != null) {
            try {
                String nick = jdbc.query("""
                        SELECT COALESCE(NULLIF(TRIM(nickname), ''), username) FROM sys_user
                        WHERE user_id = ? LIMIT 1
                        """, rs -> rs.next() ? rs.getString(1) : null, userId);
                if (StringUtils.hasText(nick)) {
                    nickname = nick;
                }
            } catch (Exception ignored) {
                // keep username
            }
        }
        return new Operator(userId, username, nickname);
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return String.valueOf(map);
        }
    }

    private static List<Long> splitIds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : text.split(",")) {
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return ids;
    }

    private static Object normalize(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof String s) {
            return s.trim();
        }
        return String.valueOf(v);
    }

    private static String display(Object v) {
        if (v == null) {
            return "空";
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? "空" : truncate(s, 120);
    }

    private static String stringify(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("jobName", "岗位"),
            Map.entry("jobDesc", "岗位职责"),
            Map.entry("salaryRange", "薪资范围"),
            Map.entry("educationReq", "学历要求"),
            Map.entry("experienceReq", "经验要求"),
            Map.entry("skillReq", "技能要求"),
            Map.entry("taskRequirement", "任务要求"),
            Map.entry("status", "状态"),
            Map.entry("locationCode", "工作地"),
            Map.entry("deptId", "部门"),
            Map.entry("headcount", "HC"),
            Map.entry("targetText", "目标到岗"),
            Map.entry("priority", "优先级"),
            Map.entry("jobPriorityCode", "岗位优先级"),
            Map.entry("workAddressId", "工作地址"),
            Map.entry("importanceLevel", "重要性"),
            Map.entry("difficultyLevel", "难度"),
            Map.entry("receivedDate", "接收日"),
            Map.entry("onboardDate", "入职日")
    );

    private record Operator(Long userId, String username, String nickname) {}
}
