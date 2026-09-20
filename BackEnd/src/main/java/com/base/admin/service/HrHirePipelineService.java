package com.base.admin.service;

import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 终面通过之后，给招聘需求负责人下发入职前任务；全部完成并上传资料后，才生成办理入职任务。
 */
@Service
@RequiredArgsConstructor
public class HrHirePipelineService {

    static final String BIZ_STEP = "hr_hire_step";
    static final String BIZ_ONBOARD = "hr_onboard";

    private static final List<Step> STEPS = List.of(
            new Step("SALARY", "薪资沟通", "薪资沟通中"),
            new Step("BG_COLLECT", "背调资料收集", "背调资料收集中"),
            new Step("BG_CHECK", "背调", "背调中"),
            new Step("MEDICAL", "体检", "体检中"),
            new Step("OFFER_PENDING", "待发offer", "待发offer"),
            new Step("PENDING_ONBOARD", "待入职", "待入职")
    );

    private final JdbcTemplate jdbc;
    private final SysTaskService taskService;

    public boolean isFinalRound(Long requisitionId, Integer roundNo) {
        Integer maxRound = maxRound(requisitionId);
        return maxRound != null && roundNo != null && roundNo >= maxRound;
    }

    public void openAfterFinal(Long requisitionId, Long applicationId) {
        touch(applicationId, "FINAL");
        Person person = person(applicationId);
        List<Long> owners = owners(requisitionId);
        if (owners.isEmpty()) {
            throw new BusinessException("该招聘需求没有负责人，无法下发入职前任务");
        }
        for (Step step : STEPS) {
            if (taskCount(applicationId, BIZ_STEP, step.code(), null) > 0) {
                continue;
            }
            SysTaskDTO task = baseTask(owners, person);
            task.setTitle("【" + step.stageName() + "】" + person.name() + " - " + person.job());
            task.setContent("终面已通过。请完成「" + step.stageName() + "」并上传完成资料，可上传多份。"
                    + "这些任务全部完成后，才会生成「办理候选人入职」任务。");
            task.setTaskType(step.taskType());
            task.setBizType(BIZ_STEP);
            task.setBizId(applicationId);
            task.setBizTitle(person.job());
            task.setRemark("hireStep:" + step.code());
            taskService.create(task);
        }
        refreshStage(applicationId);
    }

    public void afterTaskCompleted(SysTask task) {
        if (task == null || task.getBizId() == null || !StringUtils.hasText(task.getBizType())) {
            return;
        }
        Long applicationId = task.getBizId();
        if (BIZ_STEP.equals(task.getBizType())) {
            refreshStage(applicationId);
            if (allStepsDone(applicationId)) {
                openOnboard(applicationId);
            }
            return;
        }
        if (BIZ_ONBOARD.equals(task.getBizType())) {
            if (!allStepsDone(applicationId)) {
                throw new BusinessException("入职前任务尚未全部完成，不能办理候选人入职");
            }
            touch(applicationId, "ONBOARDED");
            jdbc.update("UPDATE hr_application SET current_stage = 'ONBOARDED' WHERE id = ? AND is_active = 1", applicationId);
        }
    }

    private void openOnboard(Long applicationId) {
        if (taskCount(applicationId, BIZ_ONBOARD, null, null) > 0) {
            return;
        }
        Long requisitionId = jdbc.query("""
                SELECT requisition_id FROM hr_application WHERE id = ? AND is_active = 1
                """, rs -> rs.next() && rs.getObject(1) != null ? rs.getLong(1) : null, applicationId);
        if (requisitionId == null) {
            throw new BusinessException("候选人还没有对应的招聘需求");
        }
        List<Long> owners = owners(requisitionId);
        if (owners.isEmpty()) {
            throw new BusinessException("该招聘需求没有负责人，无法下发办理入职任务");
        }
        Person person = person(applicationId);
        SysTaskDTO task = baseTask(owners, person);
        task.setTitle("【办理候选人入职】" + person.name() + " - " + person.job());
        task.setContent("薪资沟通、背调资料收集、背调、体检、待发offer、待入职均已完成。请上传入职办理资料（可多份）后完成此任务。");
        task.setTaskType("办理候选人入职");
        task.setBizType(BIZ_ONBOARD);
        task.setBizId(applicationId);
        task.setBizTitle(person.job());
        task.setRemark("hireStep:ONBOARDED");
        taskService.create(task);
    }

    private void refreshStage(Long applicationId) {
        Step current = null;
        for (Step step : STEPS) {
            if (stepDone(applicationId, step.code())) {
                touch(applicationId, step.code());
            } else if (current == null) {
                current = step;
            }
        }
        String stage = current == null ? "PENDING_ONBOARD" : current.code();
        touch(applicationId, stage);
        jdbc.update("UPDATE hr_application SET current_stage = ? WHERE id = ? AND is_active = 1", stage, applicationId);
    }

    private boolean allStepsDone(Long applicationId) {
        for (Step step : STEPS) {
            if (!stepDone(applicationId, step.code())) {
                return false;
            }
        }
        return true;
    }

    private boolean stepDone(Long applicationId, String code) {
        return taskCount(applicationId, BIZ_STEP, code, "已完成") > 0;
    }

    private int taskCount(Long applicationId, String bizType, String stepCode, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1) FROM sys_task
                WHERE biz_type = ? AND biz_id = ? AND is_active = 1
                """);
        List<Object> args = new java.util.ArrayList<>();
        args.add(bizType);
        args.add(applicationId);
        if (stepCode != null) {
            sql.append(" AND remark LIKE ? ");
            args.add("hireStep:" + stepCode + "%");
        }
        if (status != null) {
            sql.append(" AND status = ? ");
            args.add(status);
        }
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    private void touch(Long applicationId, String stage) {
        jdbc.update("""
                INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                VALUES (?, ?, NOW(), 'TASK', ?, 1)
                ON DUPLICATE KEY UPDATE is_active = 1
                """, applicationId, stage, SecurityUtils.getCurrentUsername());
    }

    private Integer maxRound(Long requisitionId) {
        return jdbc.query("""
                SELECT MAX(round_no) FROM hr_requisition_round WHERE requisition_id = ? AND is_active = 1
                """, rs -> rs.next() ? (rs.getObject(1) == null ? null : rs.getInt(1)) : null, requisitionId);
    }

    private List<Long> owners(Long requisitionId) {
        return jdbc.query("""
                SELECT user_id FROM hr_requisition_owner
                WHERE requisition_id = ? AND is_active = 1 AND user_id IS NOT NULL
                ORDER BY sort_no, id
                """, (rs, row) -> rs.getLong(1), requisitionId);
    }

    private Person person(Long applicationId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT c.display_name, COALESCE(r.job_name, '') job_name
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.id = ?
                """, applicationId);
        return new Person(String.valueOf(row.get("display_name")), String.valueOf(row.get("job_name")));
    }

    private static SysTaskDTO baseTask(List<Long> owners, Person person) {
        SysTaskDTO task = new SysTaskDTO();
        task.setPriority(3);
        task.setStatus("未开始");
        task.setProgress(0);
        task.setOwnerUserId(owners.get(0));
        task.setAssigneeUserIds(owners);
        task.setBizTitle(person.job());
        return task;
    }

    private record Step(String code, String taskType, String stageName) {}

    private record Person(String name, String job) {}
}
