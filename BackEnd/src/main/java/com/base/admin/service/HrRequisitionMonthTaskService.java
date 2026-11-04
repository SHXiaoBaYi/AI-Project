package com.base.admin.service;

import com.base.admin.domain.dto.HrRequisitionMonthTaskDTO;
import com.base.admin.domain.vo.HrRequisitionMonthTaskVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 岗位按月任务要求：P0 仅入职；P1 入职门槛与合格简历门槛为「或」——任一达标即得配置分。
 */
@Service
public class HrRequisitionMonthTaskService {

    public static final String MODE_ONBOARD = "ONBOARD";
    public static final String MODE_EITHER = "EITHER";

    private final JdbcTemplate jdbc;
    private final HrRequisitionLogService requisitionLogService;
    private final HrKpiTaskService kpiTaskService;

    public HrRequisitionMonthTaskService(JdbcTemplate jdbc,
                                        HrRequisitionLogService requisitionLogService,
                                        @Lazy HrKpiTaskService kpiTaskService) {
        this.jdbc = jdbc;
        this.requisitionLogService = requisitionLogService;
        this.kpiTaskService = kpiTaskService;
    }

    public List<HrRequisitionMonthTaskVO> listByRequisition(Long requisitionId) {
        if (requisitionId == null) {
            return List.of();
        }
        return jdbc.query("""
                SELECT t.id, t.requisition_id, r.job_name, t.settle_month, t.priority_code, t.achieve_mode,
                       t.target_count, t.resume_target, t.score, t.remark, t.update_by, t.update_time
                FROM hr_requisition_month_task t
                JOIN hr_requisition r ON r.id = t.requisition_id
                WHERE t.requisition_id = ? AND t.is_active = 1
                ORDER BY t.settle_month DESC, t.id DESC
                """, (rs, i) -> mapRow(rs), requisitionId);
    }

    /** 核算月内全部生效的 P0/P1 月度任务要求（与考核明细同源）。 */
    public List<MonthTaskRule> loadActiveRulesForMonth(String settleMonth) {
        return loadActiveAssessmentTasks(settleMonth).stream().map(ActiveAssessmentTask::rule).toList();
    }

    /** 核算月键统一为 YYYY-MM（兼容历史 2026-11-01 等写法）。 */
    public static String normalizeSettleMonthKey(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        String t = input.trim();
        return t.length() >= 7 ? t.substring(0, 7) : t;
    }

    /** 考核明细同源：月度任务 + 岗位名（JOIN 一次取齐，避免二次 IN 漏岗）。 */
    public List<ActiveAssessmentTask> loadActiveAssessmentTasks(String settleMonth) {
        if (!StringUtils.hasText(settleMonth)) {
            return List.of();
        }
        String monthKey = normalizeSettleMonthKey(settleMonth);
        List<ActiveAssessmentTask> list = new ArrayList<>();
        jdbc.query("""
                SELECT t.requisition_id, t.settle_month, t.priority_code, t.achieve_mode, t.target_count,
                       t.resume_target, t.score, r.job_name
                FROM hr_requisition_month_task t
                INNER JOIN hr_requisition r ON r.id = t.requisition_id
                INNER JOIN (
                    SELECT requisition_id, MAX(id) AS pick_id
                    FROM hr_requisition_month_task
                    WHERE is_active = 1
                      AND LEFT(TRIM(settle_month), 7) = ?
                    GROUP BY requisition_id
                ) dedup ON dedup.pick_id = t.id
                WHERE t.is_active = 1
                  AND LEFT(TRIM(t.settle_month), 7) = ?
                  AND UPPER(IFNULL(t.priority_code, '')) IN ('P0', 'P1')
                ORDER BY t.requisition_id
                """, (ResultSetExtractor<Void>) rs -> {
            // 必须显式 ResultSetExtractor：void lambda 会被当成 RowCallbackHandler，再 while(next) 会跳过首行
            while (rs.next()) {
                MonthTaskRule rule = readRule(rs);
                list.add(new ActiveAssessmentTask(rule, rs.getString("job_name")));
            }
            return null;
        }, monthKey, monthKey);
        return list;
    }

    public record ActiveAssessmentTask(MonthTaskRule rule, String jobName) {
    }

    /** 核算月内全部岗位任务要求，key=requisitionId */
    public Map<Long, MonthTaskRule> loadRulesForMonth(String settleMonth) {
        Map<Long, MonthTaskRule> map = new LinkedHashMap<>();
        for (MonthTaskRule rule : loadActiveRulesForMonth(settleMonth)) {
            map.put(rule.requisitionId(), rule);
        }
        return map;
    }

    public MonthTaskRule loadRule(Long requisitionId, String settleMonth) {
        if (requisitionId == null || !StringUtils.hasText(settleMonth)) {
            return null;
        }
        String monthKey = normalizeSettleMonthKey(settleMonth);
        return jdbc.query("""
                SELECT requisition_id, settle_month, priority_code, achieve_mode, target_count, resume_target, score
                FROM hr_requisition_month_task
                WHERE requisition_id = ? AND LEFT(TRIM(settle_month), 7) = ? AND is_active = 1
                LIMIT 1
                """, rs -> rs.next() ? readRule(rs) : null, requisitionId, monthKey);
    }

    @Transactional
    public Long save(HrRequisitionMonthTaskDTO dto) {
        if (dto == null || dto.getRequisitionId() == null) {
            throw new BusinessException("请选择岗位");
        }
        String jobName = jdbc.query("""
                SELECT job_name FROM hr_requisition WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : null, dto.getRequisitionId());
        if (jobName == null) {
            throw new BusinessException("招聘需求不存在");
        }
        String month = normalizeSettleMonthKey(dto.getSettleMonth());
        if (!StringUtils.hasText(month)) {
            throw new BusinessException("请选择核算月");
        }
        String priority = dto.getPriorityCode().trim().toUpperCase(Locale.ROOT);
        int onboard = dto.getTargetCount() == null ? 0 : dto.getTargetCount();
        int resume = dto.getResumeTarget() == null ? 0 : dto.getResumeTarget();
        int score = dto.getScore() == null ? 0 : dto.getScore();
        String mode;
        if ("P0".equals(priority)) {
            mode = MODE_ONBOARD;
            resume = 0;
            if (onboard < 1) {
                throw new BusinessException("必须到岗请填写入职人数门槛（至少 1）");
            }
        } else if ("P1".equals(priority)) {
            mode = MODE_EITHER;
            if (onboard < 1 && resume < 1) {
                throw new BusinessException("灵活到岗请至少填写「入职人数」或「合格简历份数」之一");
            }
        } else {
            throw new BusinessException("优先级只能是 P0 或 P1");
        }
        if (score < 0) {
            throw new BusinessException("得分不能为负");
        }

        Map<String, Object> before = snapshot(dto.getRequisitionId(), month);
        String username = safeUser();
        // 含软删行：唯一键 (requisition_id, settle_month) 仍占用，必须 UPDATE 恢复，不能再 INSERT
        Long existingId = jdbc.query("""
                SELECT id FROM hr_requisition_month_task
                WHERE requisition_id = ? AND LEFT(TRIM(settle_month), 7) = ?
                ORDER BY is_active DESC, id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, dto.getRequisitionId(), month);

        if (existingId != null) {
            jdbc.update("""
                    UPDATE hr_requisition_month_task
                    SET priority_code = ?, achieve_mode = ?, target_count = ?, resume_target = ?, score = ?,
                        remark = ?, update_by = ?, update_time = NOW(), is_active = 1
                    WHERE id = ?
                    """, priority, mode, onboard, resume, score, blankToNull(dto.getRemark()), username, existingId);
        } else {
            jdbc.update("""
                    INSERT INTO hr_requisition_month_task
                      (requisition_id, settle_month, priority_code, achieve_mode, target_count, resume_target, score,
                       remark, create_by, update_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE
                      priority_code = VALUES(priority_code),
                      achieve_mode = VALUES(achieve_mode),
                      target_count = VALUES(target_count),
                      resume_target = VALUES(resume_target),
                      score = VALUES(score),
                      remark = VALUES(remark),
                      update_by = VALUES(update_by),
                      update_time = NOW(),
                      is_active = 1
                    """, dto.getRequisitionId(), month, priority, mode, onboard, resume, score,
                    blankToNull(dto.getRemark()), username, username);
            existingId = jdbc.query("""
                    SELECT id FROM hr_requisition_month_task
                    WHERE requisition_id = ? AND settle_month = ? AND is_active = 1 LIMIT 1
                    """, rs -> rs.next() ? rs.getLong(1) : null, dto.getRequisitionId(), month);
        }

        Map<String, Object> after = snapshot(dto.getRequisitionId(), month);
        requisitionLogService.write(dto.getRequisitionId(), jobName, HrRequisitionLogService.ACTION_TASK,
                buildTaskSummary(before, after, month), before.isEmpty() ? null : before, after,
                dto.getEntrySource());

        kpiTaskService.syncForRequisition(dto.getRequisitionId());
        kpiTaskService.reevaluateCompletion(dto.getRequisitionId());
        return existingId;
    }

    @Transactional
    public void delete(Long id) {
        delete(id, HrRequisitionLogService.ENTRY_REQUISITION);
    }

    @Transactional
    public void delete(Long id, String entrySource) {
        if (id == null) {
            throw new BusinessException("请选择要删除的任务要求");
        }
        Map<String, Object> row = jdbc.query("""
                SELECT t.id, t.requisition_id, t.settle_month, r.job_name
                FROM hr_requisition_month_task t
                JOIN hr_requisition r ON r.id = t.requisition_id
                WHERE t.id = ? AND t.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("requisitionId", rs.getLong("requisition_id"));
            m.put("settleMonth", rs.getString("settle_month"));
            m.put("jobName", rs.getString("job_name"));
            return m;
        }, id);
        if (row == null) {
            throw new BusinessException("任务要求不存在");
        }
        Long requisitionId = ((Number) row.get("requisitionId")).longValue();
        String month = String.valueOf(row.get("settleMonth"));
        String jobName = String.valueOf(row.get("jobName"));
        Map<String, Object> before = snapshot(requisitionId, month);
        jdbc.update("""
                UPDATE hr_requisition_month_task SET is_active = 0, update_by = ?, update_time = NOW()
                WHERE id = ?
                """, safeUser(), id);
        requisitionLogService.write(requisitionId, jobName, HrRequisitionLogService.ACTION_TASK,
                "删除 " + month + " 任务要求", before, null, entrySource);
        kpiTaskService.syncForRequisition(requisitionId);
    }

    private MonthTaskRule readRule(java.sql.ResultSet rs) throws java.sql.SQLException {
        String rawPriority = rs.getString("priority_code");
        if (rawPriority == null || rawPriority.isBlank()) {
            throw new java.sql.SQLException("priority_code missing for requisition " + rs.getLong("requisition_id"));
        }
        String priority = rawPriority.trim().toUpperCase(Locale.ROOT);
        String mode = rs.getString("achieve_mode") == null ? "" : rs.getString("achieve_mode").trim().toUpperCase(Locale.ROOT);
        int onboard = Math.max(0, rs.getInt("target_count"));
        int resume = 0;
        try {
            resume = Math.max(0, rs.getInt("resume_target"));
        } catch (Exception ignored) {
            // 旧库无列时忽略
        }
        // 兼容旧 QUALIFIED_RESUME 单模式
        if ("P1".equals(priority) && "QUALIFIED_RESUME".equals(mode) && resume <= 0 && onboard > 0) {
            resume = onboard;
            onboard = 0;
            mode = MODE_EITHER;
        }
        if ("P0".equals(priority)) {
            mode = MODE_ONBOARD;
            resume = 0;
            if (onboard < 1) {
                onboard = 1;
            }
        } else {
            mode = MODE_EITHER;
        }
        return new MonthTaskRule(
                rs.getLong("requisition_id"),
                rs.getString("settle_month"),
                priority,
                mode,
                onboard,
                resume,
                Math.max(0, rs.getInt("score")));
    }

    private HrRequisitionMonthTaskVO mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        MonthTaskRule rule = readRule(rs);
        HrRequisitionMonthTaskVO vo = new HrRequisitionMonthTaskVO();
        vo.setId(rs.getLong("id"));
        vo.setRequisitionId(rule.requisitionId());
        vo.setJobName(rs.getString("job_name"));
        vo.setSettleMonth(rule.settleMonth());
        vo.setPriorityCode(rule.priorityCode());
        vo.setAchieveMode(rule.achieveMode());
        vo.setAchieveModeLabel(ruleLabel(rule));
        vo.setTargetCount(rule.onboardTarget());
        vo.setResumeTarget(rule.resumeTarget());
        vo.setScore(rule.score());
        vo.setRemark(rs.getString("remark"));
        vo.setUpdateBy(rs.getString("update_by"));
        vo.setUpdateTime(rs.getTimestamp("update_time") == null ? null
                : rs.getTimestamp("update_time").toLocalDateTime().toString().replace('T', ' '));
        return vo;
    }

    private Map<String, Object> snapshot(Long requisitionId, String settleMonth) {
        MonthTaskRule rule = loadRule(requisitionId, settleMonth);
        if (rule == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("settleMonth", rule.settleMonth());
        m.put("priorityCode", rule.priorityCode());
        m.put("onboardTarget", rule.onboardTarget());
        m.put("resumeTarget", rule.resumeTarget());
        m.put("score", rule.score());
        m.put("ruleLabel", ruleLabel(rule));
        return m;
    }

    private static String buildTaskSummary(Map<String, Object> before, Map<String, Object> after, String month) {
        if (before == null || before.isEmpty()) {
            return "新增 " + month + " 任务要求：" + formatRule(after);
        }
        return "修改 " + month + " 任务要求：" + formatRule(before) + " → " + formatRule(after);
    }

    private static String formatRule(Map<String, Object> snap) {
        if (snap == null || snap.isEmpty()) {
            return "（无）";
        }
        Object label = snap.get("ruleLabel");
        if (label != null) {
            return String.valueOf(label);
        }
        return snap.get("priorityCode") + " → " + snap.get("score") + "分";
    }

    public static String ruleLabel(MonthTaskRule rule) {
        if (rule == null) {
            return "";
        }
        if ("P0".equals(rule.priorityCode())) {
            return "必须到岗：完成 " + rule.onboardTarget() + " 个入职得 " + rule.score() + " 分";
        }
        StringBuilder sb = new StringBuilder("灵活到岗：");
        boolean first = true;
        if (rule.onboardTarget() > 0) {
            sb.append("完成 ").append(rule.onboardTarget()).append(" 个入职");
            first = false;
        }
        if (rule.resumeTarget() > 0) {
            if (!first) {
                sb.append(" 或 ");
            }
            sb.append("完成 ").append(rule.resumeTarget()).append(" 份合格简历");
        }
        sb.append(" 得 ").append(rule.score()).append(" 分");
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String safeUser() {
        try {
            String u = SecurityUtils.getCurrentUsername();
            return u == null ? "system" : u;
        } catch (Exception ex) {
            return "system";
        }
    }

    /**
     * @param onboardTarget 入职人数门槛；0 表示不按入职计
     * @param resumeTarget  合格简历门槛；0 表示不按简历计
     */
    public record MonthTaskRule(long requisitionId, String settleMonth, String priorityCode,
                                String achieveMode, int onboardTarget, int resumeTarget, int score) {
        /** 兼容旧字段名 */
        public int targetCount() {
            return onboardTarget;
        }

        public boolean allowsOnboard() {
            return onboardTarget > 0;
        }

        public boolean allowsResume() {
            return resumeTarget > 0;
        }

        @Deprecated
        public boolean onboardMode() {
            return allowsOnboard();
        }

        @Deprecated
        public boolean resumeMode() {
            return allowsResume();
        }
    }
}
