package com.base.admin.service;

import com.base.admin.domain.dto.HrRequisitionMonthTaskDTO;
import com.base.admin.domain.vo.HrRequisitionMonthTaskVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 岗位按月任务要求：P0 仅到岗；P1 到岗或合格简历二选一。
 */
@Service
public class HrRequisitionMonthTaskService {

    public static final String MODE_ONBOARD = "ONBOARD";
    public static final String MODE_RESUME = "QUALIFIED_RESUME";

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
                       t.target_count, t.score, t.remark, t.update_by, t.update_time
                FROM hr_requisition_month_task t
                JOIN hr_requisition r ON r.id = t.requisition_id
                WHERE t.requisition_id = ? AND t.is_active = 1
                ORDER BY t.settle_month DESC, t.id DESC
                """, (rs, i) -> mapRow(rs.getLong("id"), rs.getLong("requisition_id"), rs.getString("job_name"),
                rs.getString("settle_month"), rs.getString("priority_code"), rs.getString("achieve_mode"),
                rs.getInt("target_count"), rs.getInt("score"), rs.getString("remark"),
                rs.getString("update_by"), rs.getTimestamp("update_time") == null ? null
                        : rs.getTimestamp("update_time").toLocalDateTime().toString().replace('T', ' ')),
                requisitionId);
    }

    public HrRequisitionMonthTaskVO get(Long requisitionId, String settleMonth) {
        if (requisitionId == null || !StringUtils.hasText(settleMonth)) {
            return null;
        }
        String month = settleMonth.trim();
        return jdbc.query("""
                SELECT t.id, t.requisition_id, r.job_name, t.settle_month, t.priority_code, t.achieve_mode,
                       t.target_count, t.score, t.remark, t.update_by, t.update_time
                FROM hr_requisition_month_task t
                JOIN hr_requisition r ON r.id = t.requisition_id
                WHERE t.requisition_id = ? AND t.settle_month = ? AND t.is_active = 1
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return mapRow(rs.getLong("id"), rs.getLong("requisition_id"), rs.getString("job_name"),
                    rs.getString("settle_month"), rs.getString("priority_code"), rs.getString("achieve_mode"),
                    rs.getInt("target_count"), rs.getInt("score"), rs.getString("remark"),
                    rs.getString("update_by"), rs.getTimestamp("update_time") == null ? null
                            : rs.getTimestamp("update_time").toLocalDateTime().toString().replace('T', ' '));
        }, requisitionId, month);
    }

    /** 核算月内全部岗位任务要求，key=requisitionId */
    public Map<Long, MonthTaskRule> loadRulesForMonth(String settleMonth) {
        Map<Long, MonthTaskRule> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(settleMonth)) {
            return map;
        }
        jdbc.query("""
                SELECT requisition_id, settle_month, priority_code, achieve_mode, target_count, score
                FROM hr_requisition_month_task
                WHERE settle_month = ? AND is_active = 1
                """, rs -> {
            while (rs.next()) {
                long reqId = rs.getLong("requisition_id");
                map.put(reqId, new MonthTaskRule(
                        reqId,
                        rs.getString("settle_month"),
                        rs.getString("priority_code").trim().toUpperCase(Locale.ROOT),
                        rs.getString("achieve_mode").trim().toUpperCase(Locale.ROOT),
                        Math.max(1, rs.getInt("target_count")),
                        Math.max(0, rs.getInt("score"))));
            }
        }, settleMonth.trim());
        return map;
    }

    public MonthTaskRule loadRule(Long requisitionId, String settleMonth) {
        if (requisitionId == null || !StringUtils.hasText(settleMonth)) {
            return null;
        }
        return jdbc.query("""
                SELECT requisition_id, settle_month, priority_code, achieve_mode, target_count, score
                FROM hr_requisition_month_task
                WHERE requisition_id = ? AND settle_month = ? AND is_active = 1
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new MonthTaskRule(
                    rs.getLong("requisition_id"),
                    rs.getString("settle_month"),
                    rs.getString("priority_code").trim().toUpperCase(Locale.ROOT),
                    rs.getString("achieve_mode").trim().toUpperCase(Locale.ROOT),
                    Math.max(1, rs.getInt("target_count")),
                    Math.max(0, rs.getInt("score")));
        }, requisitionId, settleMonth.trim());
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
        String month = dto.getSettleMonth().trim();
        String priority = dto.getPriorityCode().trim().toUpperCase(Locale.ROOT);
        String mode = dto.getAchieveMode().trim().toUpperCase(Locale.ROOT);
        if ("P0".equals(priority)) {
            mode = MODE_ONBOARD;
        } else if ("P1".equals(priority)) {
            if (!MODE_ONBOARD.equals(mode) && !MODE_RESUME.equals(mode)) {
                throw new BusinessException("P1 任务要求只能选择「到岗」或「合格简历」其一");
            }
        } else {
            throw new BusinessException("优先级只能是 P0 或 P1");
        }
        int target = dto.getTargetCount() == null ? 1 : dto.getTargetCount();
        int score = dto.getScore() == null ? 0 : dto.getScore();
        if (target < 1) {
            throw new BusinessException("目标数量至少为 1");
        }
        if (score < 0) {
            throw new BusinessException("得分不能为负");
        }

        Map<String, Object> before = snapshot(dto.getRequisitionId(), month);
        String username = safeUser();
        Long existingId = jdbc.query("""
                SELECT id FROM hr_requisition_month_task
                WHERE requisition_id = ? AND settle_month = ? AND is_active = 1 LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, dto.getRequisitionId(), month);

        if (existingId != null) {
            jdbc.update("""
                    UPDATE hr_requisition_month_task
                    SET priority_code = ?, achieve_mode = ?, target_count = ?, score = ?, remark = ?,
                        update_by = ?, update_time = NOW()
                    WHERE id = ? AND is_active = 1
                    """, priority, mode, target, score, blankToNull(dto.getRemark()), username, existingId);
        } else {
            jdbc.update("""
                    INSERT INTO hr_requisition_month_task
                      (requisition_id, settle_month, priority_code, achieve_mode, target_count, score,
                       remark, create_by, update_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getRequisitionId(), month, priority, mode, target, score,
                    blankToNull(dto.getRemark()), username, username);
            existingId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }

        Map<String, Object> after = snapshot(dto.getRequisitionId(), month);
        String summary = buildTaskSummary(before, after, month);
        requisitionLogService.write(dto.getRequisitionId(), jobName, HrRequisitionLogService.ACTION_TASK,
                summary, before.isEmpty() ? null : before, after);

        // 当月配置变更后同步考核槽位并尝试按新规则完成
        kpiTaskService.syncForRequisition(dto.getRequisitionId());
        kpiTaskService.reevaluateCompletion(dto.getRequisitionId());
        return existingId;
    }

    @Transactional
    public void delete(Long id) {
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
                "删除 " + month + " 任务要求", before, null);
        kpiTaskService.syncForRequisition(requisitionId);
    }

    private Map<String, Object> snapshot(Long requisitionId, String settleMonth) {
        MonthTaskRule rule = loadRule(requisitionId, settleMonth);
        if (rule == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("settleMonth", rule.settleMonth());
        m.put("priorityCode", rule.priorityCode());
        m.put("achieveMode", rule.achieveMode());
        m.put("achieveModeLabel", modeLabel(rule.achieveMode()));
        m.put("targetCount", rule.targetCount());
        m.put("score", rule.score());
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
        return snap.get("priorityCode") + " / " + snap.getOrDefault("achieveModeLabel", snap.get("achieveMode"))
                + " " + snap.get("targetCount") + " → " + snap.get("score") + "分";
    }

    private static HrRequisitionMonthTaskVO mapRow(long id, long requisitionId, String jobName, String month,
                                                   String priority, String mode, int target, int score,
                                                   String remark, String updateBy, String updateTime) {
        HrRequisitionMonthTaskVO vo = new HrRequisitionMonthTaskVO();
        vo.setId(id);
        vo.setRequisitionId(requisitionId);
        vo.setJobName(jobName);
        vo.setSettleMonth(month);
        vo.setPriorityCode(priority);
        vo.setAchieveMode(mode);
        vo.setAchieveModeLabel(modeLabel(mode));
        vo.setTargetCount(target);
        vo.setScore(score);
        vo.setRemark(remark);
        vo.setUpdateBy(updateBy);
        vo.setUpdateTime(updateTime);
        return vo;
    }

    public static String modeLabel(String mode) {
        if (MODE_RESUME.equalsIgnoreCase(mode)) {
            return "合格简历";
        }
        return "到岗";
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

    public record MonthTaskRule(long requisitionId, String settleMonth, String priorityCode,
                                String achieveMode, int targetCount, int score) {
        public boolean onboardMode() {
            return !MODE_RESUME.equalsIgnoreCase(achieveMode);
        }

        public boolean resumeMode() {
            return MODE_RESUME.equalsIgnoreCase(achieveMode);
        }
    }
}
