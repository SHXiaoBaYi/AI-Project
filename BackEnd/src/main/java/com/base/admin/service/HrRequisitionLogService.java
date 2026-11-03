package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.HrRequisitionLogQueryDTO;
import com.base.admin.domain.vo.HrRequisitionLogVO;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 招聘需求 CUD 操作日志：谁、何时、做了什么、原值/新值。
 * 摘要与详情中的关联字段（部门/负责人/地址等）展示业务名称，而非裸 ID。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrRequisitionLogService {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_STATUS = "STATUS";
    /** 按月任务要求新增/修改/删除 */
    public static final String ACTION_TASK = "TASK";
    public static final String ENTRY_REQUISITION = "REQUISITION";
    public static final String ENTRY_PANEL = "PANEL";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public void write(Long requisitionId, String jobName, String action, String summary,
                      Map<String, Object> before, Map<String, Object> after) {
        write(requisitionId, jobName, action, summary, before, after, ENTRY_REQUISITION);
    }

    public void write(Long requisitionId, String jobName, String action, String summary,
                      Map<String, Object> before, Map<String, Object> after, String entrySource) {
        Operator op = currentOperator();
        String source = normalizeEntry(entrySource);
        String finalSummary = summary == null ? "" : summary;
        if (StringUtils.hasText(source) && !finalSummary.contains("来源：") && !finalSummary.startsWith("[")) {
            finalSummary = "[" + entryLabel(source) + "] " + finalSummary;
        }
        try {
            jdbc.update("""
                    INSERT INTO hr_requisition_log
                      (requisition_id, job_name, action, summary, entry_source, before_json, after_json,
                       operator_user_id, operator_username, operator_nickname, operated_at, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,?,NOW(),1)
                    """,
                    requisitionId,
                    jobName == null ? "" : jobName,
                    action,
                    truncate(finalSummary, 1000),
                    source,
                    toJson(before),
                    toJson(after),
                    op.userId(),
                    op.username(),
                    op.nickname());
        } catch (Exception e) {
            log.warn("招聘需求操作日志写入失败 action={} reqId={}: {}", action, requisitionId, e.getMessage());
        }
    }

    public static String normalizeEntry(String entrySource) {
        if (!StringUtils.hasText(entrySource)) {
            return ENTRY_REQUISITION;
        }
        String s = entrySource.trim().toUpperCase(Locale.ROOT);
        if (ENTRY_PANEL.equals(s)) {
            return ENTRY_PANEL;
        }
        return ENTRY_REQUISITION;
    }

    public static String entryLabel(String entrySource) {
        return ENTRY_PANEL.equalsIgnoreCase(entrySource) ? "招聘面板" : "招聘需求";
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
        return diffSummary(before, after, new LabelCache());
    }

    public String statusChangeSummary(Object oldStatus, Object newStatus) {
        return "状态：" + labelStatus(oldStatus) + " → " + labelStatus(newStatus);
    }

    private String diffSummary(Map<String, Object> before, Map<String, Object> after, LabelCache cache) {
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
                parts.add(FIELD_LABELS.get(key) + "：" + displayField(key, b, cache) + " → " + displayField(key, a, cache));
            }
        }
        if (!Objects.equals(normalize(before.get("ownerUserIds")), normalize(after.get("ownerUserIds")))) {
            parts.add("负责人：" + labelUsers(before.get("ownerUserIds"), cache)
                    + " → " + labelUsers(after.get("ownerUserIds"), cache));
        }
        if (!Objects.equals(normalize(before.get("interviewRounds")), normalize(after.get("interviewRounds")))) {
            parts.add("面试流程：" + formatRounds(before.get("interviewRounds"), cache)
                    + " → " + formatRounds(after.get("interviewRounds"), cache));
        }
        if (parts.isEmpty()) {
            return "保存招聘需求（字段无变化）";
        }
        return truncate(String.join("；", parts), 1000);
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
        if (StringUtils.hasText(query.getEntrySource())) {
            String entry = normalizeEntry(query.getEntrySource());
            if (ENTRY_REQUISITION.equals(entry)) {
                where.append(" AND (entry_source = ? OR entry_source IS NULL OR entry_source = '') ");
                args.add(entry);
            } else {
                where.append(" AND entry_source = ? ");
                args.add(entry);
            }
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
                SELECT id, requisition_id, job_name, action, summary, entry_source, before_json, after_json,
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
                    String entry = "";
                    try {
                        entry = rs.getString("entry_source");
                    } catch (Exception ignored) {
                        // 旧库无列
                    }
                    vo.setEntrySource(entry);
                    vo.setEntrySourceLabel(entryLabel(entry));
                    vo.setBeforeJson(rs.getString("before_json"));
                    vo.setAfterJson(rs.getString("after_json"));
                    long uid = rs.getLong("operator_user_id");
                    vo.setOperatorUserId(rs.wasNull() ? null : uid);
                    vo.setOperatorUsername(rs.getString("operator_username"));
                    vo.setOperatorNickname(rs.getString("operator_nickname"));
                    vo.setOperatedAt(rs.getString("operated_at"));
                    return vo;
                }, listArgs.toArray());

        LabelCache cache = new LabelCache();
        for (HrRequisitionLogVO vo : rows) {
            enrichRowForDisplay(vo, cache);
        }
        return new PageResult<>(t, rows);
    }

    /** 列表/详情：关联字段改成可读名称；修改类摘要按快照重算，兼容历史裸 ID 日志 */
    private void enrichRowForDisplay(HrRequisitionLogVO vo, LabelCache cache) {
        Map<String, Object> before = parseMap(vo.getBeforeJson());
        Map<String, Object> after = parseMap(vo.getAfterJson());
        String action = vo.getAction();
        String entry = vo.getEntrySource();

        if (ACTION_UPDATE.equals(action) && before != null && after != null
                && looksLikeRequisitionSnap(before) && looksLikeRequisitionSnap(after)) {
            vo.setSummary(withEntryPrefix(entry, diffSummary(before, after, cache)));
        } else if (ACTION_STATUS.equals(action) && before != null && after != null) {
            vo.setSummary(withEntryPrefix(entry, statusChangeSummary(before.get("status"), after.get("status"))));
        }

        if (before != null && !before.isEmpty()) {
            vo.setBeforeJson(toJson(toReadableSnapshot(before, cache)));
        }
        if (after != null && !after.isEmpty()) {
            vo.setAfterJson(toJson(toReadableSnapshot(after, cache)));
        }
    }

    private static boolean looksLikeRequisitionSnap(Map<String, Object> snap) {
        return snap.containsKey("jobName") || snap.containsKey("deptId") || snap.containsKey("ownerUserIds")
                || snap.containsKey("status") || snap.containsKey("workAddressId");
    }

    private static String withEntryPrefix(String entrySource, String summary) {
        if (!StringUtils.hasText(summary)) {
            return summary;
        }
        if (summary.startsWith("[")) {
            return truncate(summary, 1000);
        }
        return truncate("[" + entryLabel(entrySource) + "] " + summary, 1000);
    }

    private Map<String, Object> toReadableSnapshot(Map<String, Object> raw, LabelCache cache) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        // 月度任务快照本身已是可读文案，原样返回（仅补齐展示字段）
        if (raw.containsKey("ruleLabel") || raw.containsKey("settleMonth") && raw.containsKey("priorityCode")) {
            Map<String, Object> task = new LinkedHashMap<>(raw);
            if (task.get("ruleLabel") == null && task.get("priorityCode") != null) {
                task.put("priorityCode", labelJobPriority(task.get("priorityCode"), cache));
            }
            return task;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            switch (key) {
                case "deptId" -> {
                    out.put("deptId", val);
                    out.put("deptName", labelDept(val, cache));
                }
                case "workAddressId" -> {
                    out.put("workAddressId", val);
                    out.put("workAddressName", labelWorkAddress(val, cache));
                }
                case "ownerUserIds" -> {
                    out.put("ownerUserIds", val);
                    out.put("ownerNames", labelUsers(val, cache));
                }
                case "status" -> out.put("status", labelStatus(val));
                case "priority" -> out.put("priority", labelUrgency(val));
                case "jobPriorityCode" -> out.put("jobPriorityCode", labelJobPriority(val, cache));
                case "importanceLevel" -> out.put("importanceLevel", labelHighMidLow(val));
                case "difficultyLevel" -> out.put("difficultyLevel", labelHighMidLow(val));
                case "interviewRounds" -> out.put("interviewRounds", readableRounds(val, cache));
                default -> out.put(key, val);
            }
        }
        return out;
    }

    private Object readableRounds(Object raw, LabelCache cache) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> one = new LinkedHashMap<>();
            Object roundNo = m.get("roundNo");
            one.put("roundNo", roundNo);
            one.put("roundLabel", roundLabel(roundNo));
            Object interviewers = m.get("interviewerUserIds");
            Object ccs = m.get("ccUserIds");
            one.put("interviewerUserIds", interviewers);
            one.put("interviewerNames", labelUsers(interviewers, cache));
            one.put("ccUserIds", ccs);
            one.put("ccNames", labelUsers(ccs, cache));
            out.add(one);
        }
        return out;
    }

    private String formatRounds(Object raw, LabelCache cache) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String round = roundLabel(m.get("roundNo"));
            String interviewers = labelUsers(m.get("interviewerUserIds"), cache);
            String ccs = labelUsers(m.get("ccUserIds"), cache);
            parts.add(round + "(面试官:" + interviewers + "；抄送:" + ccs + ")");
        }
        return parts.isEmpty() ? "无" : String.join("，", parts);
    }

    private static String roundLabel(Object roundNo) {
        int n = toInt(roundNo, 0);
        return switch (n) {
            case 1 -> "一面";
            case 2 -> "二面";
            case 3 -> "三面";
            case 4 -> "四面";
            case 5 -> "五面";
            default -> n > 0 ? n + "面" : "轮次?";
        };
    }

    private String displayField(String key, Object value, LabelCache cache) {
        return switch (key) {
            case "deptId" -> labelDept(value, cache);
            case "workAddressId" -> labelWorkAddress(value, cache);
            case "status" -> labelStatus(value);
            case "priority" -> labelUrgency(value);
            case "jobPriorityCode" -> labelJobPriority(value, cache);
            case "importanceLevel", "difficultyLevel" -> labelHighMidLow(value);
            default -> display(value);
        };
    }

    private String labelDept(Object idObj, LabelCache cache) {
        Long id = toLong(idObj);
        if (id == null) {
            return "空";
        }
        return cache.depts.computeIfAbsent(id, k -> {
            try {
                String name = jdbc.query("""
                        SELECT name FROM hr_department WHERE id = ? LIMIT 1
                        """, rs -> rs.next() ? rs.getString(1) : null, k);
                return StringUtils.hasText(name) ? name : ("部门#" + k);
            } catch (Exception e) {
                return "部门#" + k;
            }
        });
    }

    private String labelWorkAddress(Object idObj, LabelCache cache) {
        Long id = toLong(idObj);
        if (id == null) {
            return "空";
        }
        return cache.addresses.computeIfAbsent(id, k -> {
            try {
                String name = jdbc.query("""
                        SELECT name FROM hr_base_dict
                        WHERE id = ? AND dict_type = 'WORK_ADDRESS' LIMIT 1
                        """, rs -> rs.next() ? rs.getString(1) : null, k);
                return StringUtils.hasText(name) ? name : ("地址#" + k);
            } catch (Exception e) {
                return "地址#" + k;
            }
        });
    }

    private String labelJobPriority(Object codeObj, LabelCache cache) {
        if (codeObj == null) {
            return "空";
        }
        String code = String.valueOf(codeObj).trim();
        if (code.isEmpty()) {
            return "空";
        }
        return cache.jobPriorities.computeIfAbsent(code, k -> {
            try {
                String name = jdbc.query("""
                        SELECT name FROM hr_base_dict
                        WHERE dict_type = 'JOB_PRIORITY' AND is_active = 1 AND code = ?
                        LIMIT 1
                        """, rs -> rs.next() ? rs.getString(1) : null, k);
                if (StringUtils.hasText(name)) {
                    return k + " " + name;
                }
            } catch (Exception ignored) {
                // fall through
            }
            return k;
        });
    }

    private String labelUsers(Object raw, LabelCache cache) {
        List<Long> ids = toIdList(raw);
        if (ids.isEmpty()) {
            return "空";
        }
        List<String> names = new ArrayList<>();
        for (Long id : ids) {
            names.add(labelUser(id, cache));
        }
        return String.join("、", names);
    }

    private String labelUser(Long id, LabelCache cache) {
        if (id == null || id <= 0) {
            return "空";
        }
        return cache.users.computeIfAbsent(id, k -> {
            try {
                String name = jdbc.query("""
                        SELECT COALESCE(NULLIF(TRIM(nickname), ''), username)
                        FROM sys_user WHERE user_id = ? LIMIT 1
                        """, rs -> rs.next() ? rs.getString(1) : null, k);
                return StringUtils.hasText(name) ? name : ("用户#" + k);
            } catch (Exception e) {
                return "用户#" + k;
            }
        });
    }

    private String labelStatus(Object raw) {
        if (raw == null) {
            return "空";
        }
        String code = String.valueOf(raw).trim();
        if (code.isEmpty()) {
            return "空";
        }
        // 已是中文标签时原样返回（详情二次 enrich）
        if ("招聘中".equals(code) || "暂缓".equals(code) || "已归档".equals(code)) {
            return code;
        }
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "OPEN" -> "招聘中";
            case "PAUSED" -> "暂缓";
            case "ARCHIVED" -> "已归档";
            default -> code;
        };
    }

    private static String labelUrgency(Object raw) {
        int v = toInt(raw, 0);
        return switch (v) {
            case 1 -> "紧急";
            case 2 -> "优先";
            case 3 -> "常规";
            default -> raw == null ? "空" : String.valueOf(raw);
        };
    }

    private static String labelHighMidLow(Object raw) {
        int v = toInt(raw, 0);
        return switch (v) {
            case 1 -> "高";
            case 2 -> "中";
            case 3 -> "低";
            default -> raw == null ? "空" : String.valueOf(raw);
        };
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
            case ACTION_TASK -> "任务要求";
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

    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return null;
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

    private static List<Long> toIdList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> col) {
            List<Long> ids = new ArrayList<>();
            for (Object o : col) {
                Long id = toLong(o);
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
            return ids;
        }
        String text = String.valueOf(raw).trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        return splitIds(text);
    }

    private static Long toLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int toInt(Object raw, int def) {
        Long v = toLong(raw);
        return v == null ? def : v.intValue();
    }

    private static Object normalize(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof String s) {
            return s.trim();
        }
        if (v instanceof Collection<?> || v instanceof Map<?, ?>) {
            return String.valueOf(v);
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

    private static final Map<String, String> FIELD_LABELS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("jobName", "岗位");
        m.put("jobDesc", "岗位职责");
        m.put("salaryRange", "薪资范围");
        m.put("educationReq", "学历要求");
        m.put("experienceReq", "经验要求");
        m.put("skillReq", "技能要求");
        m.put("taskRequirement", "任务要求");
        m.put("status", "状态");
        m.put("locationCode", "工作地");
        m.put("deptId", "部门");
        m.put("headcount", "HC");
        m.put("targetText", "目标到岗");
        m.put("priority", "优先级");
        m.put("jobPriorityCode", "岗位优先级");
        m.put("workAddressId", "工作地址");
        m.put("importanceLevel", "重要性");
        m.put("difficultyLevel", "难度");
        m.put("receivedDate", "接收日");
        m.put("onboardDate", "入职日");
        FIELD_LABELS = Map.copyOf(m);
    }

    private static final class LabelCache {
        private final Map<Long, String> depts = new HashMap<>();
        private final Map<Long, String> users = new HashMap<>();
        private final Map<Long, String> addresses = new HashMap<>();
        private final Map<String, String> jobPriorities = new HashMap<>();
    }

    private record Operator(Long userId, String username, String nickname) {}
}
