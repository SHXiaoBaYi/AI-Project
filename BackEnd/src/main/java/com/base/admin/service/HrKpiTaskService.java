package com.base.admin.service;

import com.base.admin.domain.dto.HrKpiAssessmentQueryDTO;
import com.base.admin.domain.dto.HrKpiScoreRuleDTO;
import com.base.admin.domain.vo.HrKpiAssessmentVO;
import com.base.admin.domain.vo.HrKpiScoreRuleVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.service.HrRequisitionMonthTaskService.MonthTaskRule;
import com.base.admin.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * P0/P1 考核任务：按岗位「按月任务要求」生成与计分；入职/合格简历自动完成。
 */
@Slf4j
@Service
public class HrKpiTaskService {

    private static final Set<String> SCORE_PRIORITIES = Set.of("P0", "P1");

    private final JdbcTemplate jdbc;
    private final HrRequisitionMonthTaskService monthTaskService;

    public HrKpiTaskService(JdbcTemplate jdbc, @Lazy HrRequisitionMonthTaskService monthTaskService) {
        this.jdbc = jdbc;
        this.monthTaskService = monthTaskService;
    }

    /** 需求保存/改状态/改负责人或 HC 后同步任务槽位（当前自然月）。 */
    @Transactional
    public void syncForRequisition(Long requisitionId) {
        syncForRequisition(requisitionId, YearMonth.now());
    }

    /**
     * 按核算月同步单需求考核任务：以「月度任务要求」为准；到期日落该自然月末。
     * 仅当核算月=当前月时取消无效 OPEN；历史月只补/修该月槽位，避免误伤其它月份任务。
     */
    @Transactional
    public void syncForRequisition(Long requisitionId, YearMonth settleYm) {
        if (requisitionId == null || settleYm == null) {
            return;
        }
        Map<String, Object> req = jdbc.query("""
                SELECT id, status, headcount, job_priority_code, received_date, create_time
                FROM hr_requisition WHERE id = ? AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("status", rs.getString("status"));
            m.put("headcount", rs.getInt("headcount"));
            m.put("priority", rs.getString("job_priority_code"));
            m.put("receivedDate", rs.getDate("received_date") == null ? null : rs.getDate("received_date").toLocalDate());
            m.put("createTime", rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
            return m;
        }, requisitionId);
        if (req == null) {
            return;
        }
        String status = String.valueOf(req.get("status"));
        String settleMonth = settleYm.toString();
        MonthTaskRule monthRule = monthTaskService.loadRule(requisitionId, settleMonth);
        String priority = monthRule != null ? monthRule.priorityCode()
                : (req.get("priority") == null ? "" : String.valueOf(req.get("priority")).trim().toUpperCase());
        List<Long> owners = loadOwners(requisitionId);
        boolean currentMonth = settleYm.equals(YearMonth.now());

        if (!"OPEN".equals(status)) {
            jdbc.update("""
                    UPDATE hr_kpi_task SET status = 'CANCELLED', update_time = NOW()
                    WHERE requisition_id = ? AND status = 'OPEN' AND is_active = 1
                    """, requisitionId);
            return;
        }
        if (owners.isEmpty() || monthRule == null || !SCORE_PRIORITIES.contains(priority)) {
            if (currentMonth) {
                jdbc.update("""
                        UPDATE hr_kpi_task SET status = 'CANCELLED', update_time = NOW()
                        WHERE requisition_id = ? AND status = 'OPEN' AND is_active = 1
                        """, requisitionId);
            }
            return;
        }

        LocalDate publishDay = req.get("receivedDate") instanceof LocalDate d ? d : null;
        if (publishDay == null && req.get("createTime") instanceof LocalDateTime ct) {
            publishDay = ct.toLocalDate();
        }
        if (publishDay == null) {
            publishDay = LocalDate.now();
        }
        LocalDateTime published = publishDay.atStartOfDay();
        LocalDateTime monthFrom = settleYm.atDay(1).atStartOfDay();
        LocalDateTime monthTo = settleYm.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime due = endOfMonth(monthFrom);
        String username = safeUser();
        for (Long ownerId : owners) {
            syncOwnerTaskForMonth(requisitionId, ownerId, settleYm, priority, published, due, username);
        }
        if (currentMonth) {
            repairSchedulesForRequisition(requisitionId, published, due);
            StringBuilder in = new StringBuilder();
            List<Object> args = new ArrayList<>();
            args.add(requisitionId);
            for (int i = 0; i < owners.size(); i++) {
                if (i > 0) {
                    in.append(',');
                }
                in.append('?');
                args.add(owners.get(i));
            }
            jdbc.update("""
                    UPDATE hr_kpi_task SET status = 'CANCELLED', update_time = NOW()
                    WHERE requisition_id = ? AND status = 'OPEN' AND is_active = 1
                      AND owner_user_id NOT IN (""" + in + ")", args.toArray());
        }
    }

    /** 单负责人在某核算月：最多 1 条 OPEN（未达标）或已有 DONE。 */
    private void syncOwnerTaskForMonth(Long requisitionId, Long ownerId, YearMonth settleYm, String priority,
                                       LocalDateTime published, LocalDateTime due, String username) {
        LocalDateTime monthFrom = settleYm.atDay(1).atStartOfDay();
        LocalDateTime monthTo = settleYm.plusMonths(1).atDay(1).atStartOfDay();
        if (countDoneInDueMonth(requisitionId, ownerId, monthFrom, monthTo) > 0) {
            cancelOpenTasksInDueMonth(requisitionId, ownerId, monthFrom, monthTo);
            return;
        }
        List<Long> openIdsInMonth = listOpenTaskIdsInDueMonth(requisitionId, ownerId, monthFrom, monthTo);
        if (openIdsInMonth.isEmpty()) {
            int slot = maxSlot(requisitionId, ownerId) + 1;
            jdbc.update("""
                    INSERT INTO hr_kpi_task (requisition_id, owner_user_id, slot_no, priority_code,
                      published_at, due_at, origin_due_at, status, score, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', 0, ?, 1)
                    ON DUPLICATE KEY UPDATE
                      priority_code = VALUES(priority_code),
                      published_at = IF(hr_kpi_task.status = 'CANCELLED', VALUES(published_at), hr_kpi_task.published_at),
                      due_at = IF(hr_kpi_task.status = 'CANCELLED', VALUES(due_at), hr_kpi_task.due_at),
                      origin_due_at = IF(hr_kpi_task.status = 'CANCELLED', VALUES(origin_due_at), hr_kpi_task.origin_due_at),
                      status = 'OPEN', is_active = 1,
                      score = IF(hr_kpi_task.status = 'CANCELLED', 0, hr_kpi_task.score),
                      done_at = IF(hr_kpi_task.status = 'CANCELLED', NULL, hr_kpi_task.done_at),
                      done_reason = IF(hr_kpi_task.status = 'CANCELLED', NULL, hr_kpi_task.done_reason),
                      done_application_id = IF(hr_kpi_task.status = 'CANCELLED', NULL, hr_kpi_task.done_application_id)
                    """, requisitionId, ownerId, slot, priority, published, due, due, username);
            openIdsInMonth = listOpenTaskIdsInDueMonth(requisitionId, ownerId, monthFrom, monthTo);
        }
        while (openIdsInMonth.size() > 1) {
            Long dropId = openIdsInMonth.remove(openIdsInMonth.size() - 1);
            jdbc.update("""
                    UPDATE hr_kpi_task SET status = 'CANCELLED', update_time = NOW()
                    WHERE id = ? AND status = 'OPEN' AND is_active = 1
                    """, dropId);
        }
        if (!openIdsInMonth.isEmpty()) {
            Long keepId = openIdsInMonth.get(0);
            jdbc.update("""
                    UPDATE hr_kpi_task SET priority_code = ?, update_time = NOW()
                    WHERE id = ? AND status = 'OPEN' AND is_active = 1
                    """, priority, keepId);
            jdbc.update("""
                    UPDATE hr_kpi_task SET due_at = ?, origin_due_at = ?, update_time = NOW()
                    WHERE id = ? AND status = 'OPEN' AND is_active = 1
                      AND (due_at = origin_due_at OR origin_due_at IS NULL)
                    """, due, due, keepId);
        }
    }

    /** 候选人变为已入职后，按规则尝试完成各负责人 OPEN 任务。 */
    @Transactional
    public void onApplicationOnboarded(Long applicationId) {
        if (applicationId == null) {
            return;
        }
        Long requisitionId = jdbc.query("""
                SELECT requisition_id FROM hr_application WHERE id = ? AND is_active = 1
                """, rs -> rs.next() && rs.getObject(1) != null ? rs.getLong(1) : null, applicationId);
        if (requisitionId == null) {
            return;
        }
        LocalDateTime onboardAt = onboardEventAt(applicationId);
        if (onboardAt == null) {
            onboardAt = LocalDateTime.now();
        }
        List<Long> owners = loadOwners(requisitionId);
        for (Long ownerId : owners) {
            tryCompleteByOnboard(requisitionId, ownerId, onboardAt, applicationId);
            tryCompleteByQualifiedResumes(requisitionId, ownerId);
        }
        syncForRequisition(requisitionId);
    }

    /** 面试评价写入合格简历后，尝试按当月任务要求（合格简历模式）完成任务。 */
    @Transactional
    public void onResumeQualifiedChanged(Long requisitionId) {
        if (requisitionId == null) {
            return;
        }
        List<Long> owners = loadOwners(requisitionId);
        for (Long ownerId : owners) {
            tryCompleteByQualifiedResumes(requisitionId, ownerId);
        }
        syncForRequisition(requisitionId);
    }

    /** 月度任务要求变更后，按新规则重试完成。 */
    @Transactional
    public void reevaluateCompletion(Long requisitionId) {
        if (requisitionId == null) {
            return;
        }
        List<Long> owners = loadOwners(requisitionId);
        for (Long ownerId : owners) {
            tryCompleteByOnboard(requisitionId, ownerId, LocalDateTime.now(), null);
            tryCompleteByQualifiedResumes(requisitionId, ownerId);
        }
    }

    @Transactional
    public void updateDueAt(Long taskId, LocalDateTime dueAt) {
        if (taskId == null || dueAt == null) {
            throw new BusinessException("请选择新的到期时间");
        }
        Map<String, Object> row = jdbc.query("""
                SELECT id, status FROM hr_kpi_task WHERE id = ? AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("status", rs.getString("status"));
            return m;
        }, taskId);
        if (row == null) {
            throw new BusinessException("考核任务不存在");
        }
        if (!"OPEN".equals(row.get("status"))) {
            throw new BusinessException("只有未完成的任务可以延期");
        }
        int n = jdbc.update("UPDATE hr_kpi_task SET due_at = ?, update_time = NOW() WHERE id = ? AND is_active = 1",
                dueAt, taskId);
        if (n == 0) {
            throw new BusinessException("考核任务不存在");
        }
    }

    /**
     * 同步全部招聘中的 P0/P1 任务槽，并按已入职/合格简历尝试完成。
     */
    @Transactional
    public int syncAllOpenScoreRequisitions() {
        return syncForSettleMonth(YearMonth.now());
    }

    /**
     * 按核算月同步所有应参与考核的在招岗位（含仅在「月度任务要求」中配置 P0/P1、需求表未填岗位优先级的岗）。
     */
    @Transactional
    public int syncForSettleMonth(YearMonth settleYm) {
        if (settleYm == null || settleYm.isAfter(YearMonth.now())) {
            return 0;
        }
        List<Long> ids = listOpenRequisitionIdsForKpiSync(settleYm);
        int n = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            syncForRequisition(id, settleYm);
            if (settleYm.equals(YearMonth.now())) {
                for (Long ownerId : loadOwners(id)) {
                    tryCompleteByOnboard(id, ownerId, now, null);
                    tryCompleteByQualifiedResumes(id, ownerId);
                }
            }
            n++;
        }
        if (settleYm.equals(YearMonth.now())) {
            repairAllTaskSchedulesFromRequisitions();
        }
        return n;
    }

    /**
     * 在招且已绑定系统用户负责人，且（需求岗位优先级为 P0/P1 或 该月已配置 P0/P1 月度任务）。
     */
    private List<Long> listOpenRequisitionIdsForKpiSync(YearMonth settleYm) {
        String month = settleYm.toString();
        return jdbc.query("""
                SELECT DISTINCT r.id
                FROM hr_requisition r
                WHERE r.is_active = 1 AND r.status = 'OPEN'
                  AND EXISTS (
                    SELECT 1 FROM hr_requisition_owner o
                    WHERE o.requisition_id = r.id AND o.is_active = 1 AND o.user_id IS NOT NULL
                  )
                  AND (
                    UPPER(IFNULL(r.job_priority_code, '')) IN ('P0', 'P1')
                    OR EXISTS (
                      SELECT 1 FROM hr_requisition_month_task t
                      WHERE t.requisition_id = r.id AND LEFT(TRIM(t.settle_month), 7) = LEFT(TRIM(?), 7)
                        AND t.is_active = 1
                        AND UPPER(IFNULL(t.priority_code, '')) IN ('P0', 'P1')
                    )
                  )
                ORDER BY r.id
                """, (rs, i) -> rs.getLong(1), month);
    }

    /**
     * 按岗位收到日重算发布/到期。已手动延期（due_at ≠ origin_due_at）的只改发布日。
     */
    @Transactional
    public int repairAllTaskSchedulesFromRequisitions() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id, t.status, t.due_at, t.origin_due_at, t.done_at,
                       COALESCE(r.received_date, DATE(r.create_time)) AS start_day
                FROM hr_kpi_task t
                JOIN hr_requisition r ON r.id = t.requisition_id AND r.is_active = 1
                WHERE t.is_active = 1 AND t.status IN ('OPEN', 'DONE')
                """);
        int n = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String status = String.valueOf(row.get("status"));
            LocalDate startDay = toLocalDate(row.get("start_day"));
            if (startDay == null) {
                startDay = LocalDate.now();
            }
            LocalDateTime published = startDay.atStartOfDay();
            LocalDateTime dueAt = toLocalDateTime(row.get("due_at"));
            LocalDateTime originDue = toLocalDateTime(row.get("origin_due_at"));
            boolean postponed = dueAt != null && originDue != null && !dueAt.equals(originDue);
            LocalDateTime newDue;
            if ("DONE".equals(status)) {
                LocalDateTime doneAt = toLocalDateTime(row.get("done_at"));
                newDue = doneAt != null ? endOfMonth(doneAt) : endOfMonth(startDay.atStartOfDay());
            } else {
                newDue = endOfMonth(YearMonth.now().atDay(1).atStartOfDay());
            }
            if (postponed) {
                n += jdbc.update("""
                        UPDATE hr_kpi_task SET published_at = ?, update_time = NOW() WHERE id = ? AND is_active = 1
                        """, published, id);
            } else {
                n += jdbc.update("""
                        UPDATE hr_kpi_task
                        SET published_at = ?, due_at = ?, origin_due_at = ?, update_time = NOW()
                        WHERE id = ? AND is_active = 1
                        """, published, newDue, newDue, id);
            }
        }
        if (n > 0) {
            log.info("考核任务发布/到期已按岗位收到日纠偏 {} 条", n);
        }
        return n;
    }

    private void repairSchedulesForRequisition(Long requisitionId, LocalDateTime published, LocalDateTime openDue) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, status, due_at, origin_due_at, done_at
                FROM hr_kpi_task
                WHERE requisition_id = ? AND is_active = 1 AND status IN ('OPEN', 'DONE')
                """, requisitionId);
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String status = String.valueOf(row.get("status"));
            LocalDateTime dueAt = toLocalDateTime(row.get("due_at"));
            LocalDateTime originDue = toLocalDateTime(row.get("origin_due_at"));
            boolean postponed = dueAt != null && originDue != null && !dueAt.equals(originDue);
            LocalDateTime newDue = openDue;
            if ("DONE".equals(status)) {
                LocalDateTime doneAt = toLocalDateTime(row.get("done_at"));
                newDue = doneAt != null ? endOfMonth(doneAt) : openDue;
            }
            if (postponed) {
                jdbc.update("UPDATE hr_kpi_task SET published_at = ?, update_time = NOW() WHERE id = ?",
                        published, id);
            } else {
                jdbc.update("""
                        UPDATE hr_kpi_task
                        SET published_at = ?, due_at = ?, origin_due_at = ?, update_time = NOW()
                        WHERE id = ?
                        """, published, newDue, newDue, id);
            }
        }
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof java.util.Date d) {
            return new java.sql.Date(d.getTime()).toLocalDate();
        }
        if (value instanceof LocalDateTime t) {
            return t.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime t) {
            return t;
        }
        if (value instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay();
        }
        if (value instanceof LocalDate d) {
            return d.atStartOfDay();
        }
        String s = String.valueOf(value).replace('T', ' ');
        if (s.length() >= 19) {
            return LocalDateTime.parse(s.substring(0, 19).replace(' ', 'T'));
        }
        if (s.length() >= 10) {
            return LocalDate.parse(s.substring(0, 10)).atStartOfDay();
        }
        return null;
    }

    /**
     * 全量历史回填：按入职时间线与合格简历重建所有 P0/P1 考核任务（含已关闭岗位），
     * 完成任务的到期日落在完成当月，便于按月核算历史得分。
     */
    @Transactional
    public int backfillAllHistorical() {
        int cleared = jdbc.update("UPDATE hr_kpi_task SET is_active = 0 WHERE is_active = 1");
        log.info("考核任务历史回填：先软删旧任务 {} 条", cleared);
        List<Map<String, Object>> monthTasks = jdbc.queryForList("""
                SELECT t.requisition_id, t.settle_month, t.priority_code, t.achieve_mode, t.target_count,
                       IFNULL(t.resume_target, 0) AS resume_target, t.score,
                       COALESCE(r.received_date, DATE(r.create_time)) AS start_day
                FROM hr_requisition_month_task t
                JOIN hr_requisition r ON r.id = t.requisition_id AND r.is_active = 1
                WHERE t.is_active = 1
                ORDER BY t.requisition_id, t.settle_month
                """);
        int reqCount = 0;
        int taskCount = 0;
        Long lastReq = null;
        for (Map<String, Object> mt : monthTasks) {
            Long requisitionId = ((Number) mt.get("requisition_id")).longValue();
            if (!Objects.equals(lastReq, requisitionId)) {
                reqCount++;
                lastReq = requisitionId;
            }
            String settleMonth = String.valueOf(mt.get("settle_month"));
            String priority = String.valueOf(mt.get("priority_code")).trim().toUpperCase();
            String mode = String.valueOf(mt.get("achieve_mode")).trim().toUpperCase();
            int onboardNeed = Math.max(0, ((Number) mt.get("target_count")).intValue());
            int resumeNeed = Math.max(0, ((Number) mt.get("resume_target")).intValue());
            // 兼容旧「仅合格简历」配置
            if ("P1".equals(priority) && "QUALIFIED_RESUME".equals(mode) && resumeNeed <= 0 && onboardNeed > 0) {
                resumeNeed = onboardNeed;
                onboardNeed = 0;
            }
            int score = Math.max(0, ((Number) mt.get("score")).intValue());
            LocalDate startDay = toLocalDate(mt.get("start_day"));
            if (startDay == null) {
                startDay = LocalDate.now();
            }
            YearMonth ym = YearMonth.parse(settleMonth);
            LocalDateTime monthFrom = ym.atDay(1).atStartOfDay();
            LocalDateTime monthTo = ym.plusMonths(1).atDay(1).atStartOfDay();
            LocalDateTime due = endOfMonth(monthFrom);
            LocalDateTime published = startDay.atStartOfDay();
            List<Long> owners = loadOwners(requisitionId);
            if (owners.isEmpty()) {
                continue;
            }
            List<OnboardHit> onboards = listOnboards(requisitionId).stream()
                    .filter(h -> !h.at().isBefore(monthFrom) && h.at().isBefore(monthTo))
                    .toList();
            List<LocalDateTime> qualifiedAts = listQualifiedAts(requisitionId).stream()
                    .filter(at -> !at.isBefore(monthFrom) && at.isBefore(monthTo))
                    .toList();
            LocalDateTime doneAt = null;
            String doneReason = null;
            Long doneAppId = null;
            boolean byOnboard = onboardNeed > 0 && onboards.size() >= onboardNeed;
            boolean byResume = resumeNeed > 0 && qualifiedAts.size() >= resumeNeed;
            if (byOnboard) {
                OnboardHit hit = onboards.get(onboardNeed - 1);
                doneAt = hit.at();
                doneReason = "ONBOARD";
                doneAppId = hit.applicationId();
            } else if (byResume) {
                doneAt = qualifiedAts.get(resumeNeed - 1);
                doneReason = "QUALIFIED_RESUME";
            } else {
                continue;
            }
            // 每岗位每负责人每月最多记 1 次配置分（入职或简历任一达标即可）
            for (Long ownerId : owners) {
                int slot = maxSlot(requisitionId, ownerId) + 1;
                insertHistoricalTask(requisitionId, ownerId, slot, priority, published, due,
                        "DONE", doneAt, doneReason, doneAppId, score);
                taskCount++;
            }
        }
        syncAllOpenScoreRequisitions();
        log.info("考核任务历史回填完成：配置过月度任务的需求约 {} 个，写入任务约 {} 条", reqCount, taskCount);
        return reqCount;
    }

    private void insertHistoricalTask(Long requisitionId, Long ownerId, int slot, String priority,
                                      LocalDateTime published, LocalDateTime due, String status,
                                      LocalDateTime doneAt, String doneReason, Long applicationId, int score) {
        jdbc.update("""
                INSERT INTO hr_kpi_task (requisition_id, owner_user_id, slot_no, priority_code,
                  published_at, due_at, origin_due_at, status, done_at, done_reason, done_application_id,
                  score, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'kpi-backfill', 1)
                ON DUPLICATE KEY UPDATE
                  priority_code = VALUES(priority_code),
                  published_at = VALUES(published_at),
                  due_at = VALUES(due_at),
                  origin_due_at = VALUES(origin_due_at),
                  status = VALUES(status),
                  done_at = VALUES(done_at),
                  done_reason = VALUES(done_reason),
                  done_application_id = VALUES(done_application_id),
                  score = VALUES(score),
                  create_by = VALUES(create_by),
                  is_active = 1
                """, requisitionId, ownerId, slot, priority, published, due, due,
                status, doneAt, doneReason, applicationId, score);
    }

    private List<OnboardHit> listOnboards(Long requisitionId) {
        return jdbc.query("""
                SELECT a.id AS application_id,
                       COALESCE(
                         (SELECT MIN(e.event_at) FROM hr_stage_event e
                           WHERE e.application_id = a.id AND e.stage_code = 'ONBOARDED' AND e.is_active = 1),
                         a.update_time, a.create_time
                       ) AS onboard_at
                FROM hr_application a
                WHERE a.requisition_id = ? AND a.is_active = 1 AND a.current_stage = 'ONBOARDED'
                ORDER BY onboard_at ASC, a.id ASC
                """, (rs, i) -> new OnboardHit(
                rs.getLong("application_id"),
                rs.getTimestamp("onboard_at").toLocalDateTime()), requisitionId);
    }

    private List<LocalDateTime> listQualifiedAts(Long requisitionId) {
        // 已入职人选走入职记分，其合格简历不再计入简历达标，避免重复
        return jdbc.query("""
                SELECT MIN(COALESCE(rec.interviewed_at, rec.update_time, rec.create_time)) AS hit_at
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                WHERE a.requisition_id = ? AND rec.is_active = 1 AND rec.resume_qualified = 1
                  AND a.current_stage <> 'ONBOARDED'
                GROUP BY rec.application_id
                ORDER BY hit_at ASC
                """, (rs, i) -> rs.getTimestamp("hit_at").toLocalDateTime(), requisitionId);
    }

    private static LocalDateTime endOfMonth(LocalDateTime at) {
        LocalDate d = at.toLocalDate().withDayOfMonth(1).plusMonths(1);
        return d.atStartOfDay().minusSeconds(1);
    }

    private record OnboardHit(long applicationId, LocalDateTime at) {
    }

    /** 个人满分回退（无任务权重时用于展示）。 */
    public static final double ASSESS_BASE_SCORE_DEFAULT = 30.0;

    public HrKpiAssessmentVO assessment(HrKpiAssessmentQueryDTO query) {
        HrKpiAssessmentQueryDTO q = query == null ? new HrKpiAssessmentQueryDTO() : query;
        LocalDate month = q.getSettleMonth() == null ? LocalDate.now() : q.getSettleMonth();
        LocalDate monthStart = month.withDayOfMonth(1);
        YearMonth settleYm = YearMonth.from(monthStart);
        // 按所选核算月补全 hr_kpi_task（修复仅配月度任务、未填需求岗位优先级时不生成任务）
        syncForSettleMonth(settleYm);
        LocalDateTime from = monthStart.atStartOfDay();
        LocalDateTime to = monthStart.plusMonths(1).atStartOfDay();
        String settleMonthKey = settleYm.toString();
        List<HrRequisitionMonthTaskService.ActiveAssessmentTask> activeTasks =
                monthTaskService.loadActiveAssessmentTasks(settleMonthKey);
        // 入职/合格简历只认核算月窗口；纠正误标 DONE（如用了上月简历）
        reevaluateCompletionForSettleMonth(settleYm, activeTasks, from, to);
        Map<Long, MonthTaskRule> monthRules = new LinkedHashMap<>();
        for (HrRequisitionMonthTaskService.ActiveAssessmentTask item : activeTasks) {
            monthRules.put(item.rule().requisitionId(), item.rule());
        }

        boolean settleCurrentMonth = monthStart.equals(LocalDate.now().withDayOfMonth(1));

        List<Long> ownerIds = q.getOwnerUserIds() == null ? List.of()
                : q.getOwnerUserIds().stream().filter(Objects::nonNull).distinct().toList();

        Map<Long, MonthAttainment> attainmentByReq = new LinkedHashMap<>();
        for (HrRequisitionMonthTaskService.ActiveAssessmentTask item : activeTasks) {
            attainmentByReq.put(item.rule().requisitionId(),
                    evaluateMonthAttainment(item.rule(), item.rule().requisitionId(), from, to));
        }

        List<HrKpiAssessmentVO.TaskRow> allTasks = buildTasksFromMonthRequirements(
                activeTasks, from, to, settleCurrentMonth, ownerIds, attainmentByReq);

        Map<Long, HrKpiAssessmentVO.OwnerSummary> owners = new LinkedHashMap<>();
        for (HrKpiAssessmentVO.TaskRow t : allTasks) {
            long ownerKey = t.getOwnerUserId() == null ? 0L : t.getOwnerUserId();
            HrKpiAssessmentVO.OwnerSummary s = owners.computeIfAbsent(ownerKey, id -> {
                HrKpiAssessmentVO.OwnerSummary o = new HrKpiAssessmentVO.OwnerSummary();
                o.setOwnerUserId(t.getOwnerUserId());
                o.setOwnerName(t.getOwnerName());
                o.setDueCount(0);
                o.setDoneCount(0);
                o.setOpenCount(0);
                o.setP0Due(0);
                o.setP0Done(0);
                o.setP1Due(0);
                o.setP1Done(0);
                o.setP1DoneOnboard(0);
                o.setP1DoneResume(0);
                o.setP1ResumePartialCount(0);
                o.setP1ResumePartialScore(0.0);
                o.setTotalWeight(0.0);
                o.setDoneWeight(0.0);
                o.setCompletionRate(0.0);
                o.setScore(0.0);
                return o;
            });
            double weight = t.getWeight() == null ? 0.0 : t.getWeight();
            double earned = t.getEarnedWeight() == null ? 0.0 : t.getEarnedWeight();
            s.setDueCount(s.getDueCount() + 1);
            s.setTotalWeight(round2(s.getTotalWeight() + weight));
            boolean p0 = "P0".equalsIgnoreCase(t.getPriorityCode());
            if (p0) {
                s.setP0Due(s.getP0Due() + 1);
            } else {
                s.setP1Due(s.getP1Due() + 1);
            }
            if ("DONE".equals(t.getStatus())) {
                s.setDoneCount(s.getDoneCount() + 1);
                s.setDoneWeight(round2(s.getDoneWeight() + earned));
                if (p0) {
                    s.setP0Done(s.getP0Done() + 1);
                } else {
                    s.setP1Done(s.getP1Done() + 1);
                    String reason = t.getDoneReason() == null ? "" : t.getDoneReason().trim();
                    if ("QUALIFIED_RESUME".equals(reason)) {
                        s.setP1DoneResume(s.getP1DoneResume() + 1);
                    } else {
                        s.setP1DoneOnboard(s.getP1DoneOnboard() + 1);
                    }
                }
            } else {
                s.setOpenCount(s.getOpenCount() + 1);
            }
        }
        double sumOwnerEarned = 0.0;
        double sumOwnerMax = 0.0;
        for (HrKpiAssessmentVO.OwnerSummary s : owners.values()) {
            double total = s.getTotalWeight() == null ? 0.0 : s.getTotalWeight();
            double done = s.getDoneWeight() == null ? 0.0 : s.getDoneWeight();
            double rate = total > 0 ? done / total : 0.0;
            s.setCompletionRate(round4(rate));
            double earned = round2(done);
            s.setScore(earned);
            s.setDoneWeight(earned);
            s.setTotalWeight(round2(total));
            sumOwnerEarned += earned;
            sumOwnerMax += total;
        }

        int ownerCount = owners.size();
        double overallEarned = round2(sumOwnerEarned);
        double overallMax = round2(sumOwnerMax);
        double overallRate = overallMax > 0 ? overallEarned / overallMax : 0.0;

        String statusFilter = StringUtils.hasText(q.getStatus()) ? q.getStatus().trim().toUpperCase() : "";
        List<HrKpiAssessmentVO.TaskRow> tasks = statusFilter.isEmpty() ? allTasks
                : allTasks.stream().filter(t -> statusFilter.equalsIgnoreCase(t.getStatus())).toList();

        double avgP0 = monthRules.values().stream().filter(r -> "P0".equals(r.priorityCode()))
                .mapToInt(MonthTaskRule::score).average().orElse(0);
        double avgP1 = monthRules.values().stream().filter(r -> "P1".equals(r.priorityCode()))
                .mapToInt(MonthTaskRule::score).average().orElse(0);
        int sampleResumeNeed = monthRules.values().stream().filter(MonthTaskRule::allowsResume)
                .mapToInt(MonthTaskRule::resumeTarget).findFirst().orElse(0);
        int sampleOnboardNeed = monthRules.values().stream().filter(MonthTaskRule::allowsOnboard)
                .mapToInt(MonthTaskRule::onboardTarget).findFirst().orElse(1);

        HrKpiAssessmentVO vo = new HrKpiAssessmentVO();
        vo.setSettleMonth(monthStart);
        // 个人卡片分母仍用各自任务权重；整体分母用当月岗位配置总分
        vo.setBaseScore(overallMax);
        vo.setWeightP0(round2(avgP0));
        vo.setWeightP1(round2(avgP1));
        vo.setP0OnboardCount(sampleOnboardNeed);
        vo.setP1QualifiedResumeCount(sampleResumeNeed);
        vo.setP1OnboardCount(sampleOnboardNeed);
        vo.setOwnerCount(ownerCount);
        vo.setOverallTotalWeight(overallMax);
        vo.setOverallDoneWeight(overallEarned);
        vo.setOverallCompletionRate(round4(overallRate));
        vo.setOverallScore(overallEarned);
        vo.setOverallScoreMax(overallMax);
        int configuredJobs = activeTasks.size();
        double configuredTotal = round2(activeTasks.stream().mapToInt(t -> t.rule().score()).sum());
        vo.setScoreRuleHint(String.format(
                "%s 生效任务要求 %d 个岗位（配置总分 %.0f）：任务明细与计分均按当月生效的月度任务要求；"
                        + "必须到岗按入职达标；灵活到岗入职或合格简历任一达标；每岗位每负责人一行；未达不得分。",
                settleMonthKey, configuredJobs, configuredTotal));
        vo.setOwners(new ArrayList<>(owners.values()));
        vo.setTasks(tasks);
        return vo;
    }

    /**
     * 任务明细与计分：以核算月「生效的月度任务要求」为全集（SQL 驱动，避免 Map 漏岗），每岗位×负责人一行。
     */
    private List<HrKpiAssessmentVO.TaskRow> buildTasksFromMonthRequirements(
            List<HrRequisitionMonthTaskService.ActiveAssessmentTask> activeTasks,
            LocalDateTime monthFrom,
            LocalDateTime monthTo,
            boolean settleCurrentMonth,
            List<Long> ownerFilter,
            Map<Long, MonthAttainment> attainmentByReq) {
        if (activeTasks == null || activeTasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> reqIds = activeTasks.stream().map(t -> t.rule().requisitionId()).distinct().toList();
        Map<Long, List<OwnerRef>> ownersByReq = loadRequisitionOwnerRefs(reqIds);
        Map<OwnerJobKey, List<HrKpiAssessmentVO.TaskRow>> kpiByKey =
                loadKpiTaskRowsByOwnerJob(reqIds, monthFrom, monthTo, settleCurrentMonth);

        LocalDateTime defaultDue = endOfMonth(monthFrom);
        List<HrKpiAssessmentVO.TaskRow> rows = new ArrayList<>();
        for (HrRequisitionMonthTaskService.ActiveAssessmentTask item : activeTasks) {
            MonthTaskRule rule = item.rule();
            Long reqId = rule.requisitionId();
            String jobName = item.jobName();
            if (!StringUtils.hasText(jobName)) {
                jobName = "岗位#" + reqId;
            }
            MonthAttainment attainment = attainmentByReq == null ? MonthAttainment.notDone()
                    : attainmentByReq.getOrDefault(reqId, MonthAttainment.notDone());
            List<OwnerRef> owners = ownersByReq.get(reqId);
            if (owners == null || owners.isEmpty()) {
                owners = List.of(new OwnerRef(null, null));
            }
            int rowsBefore = rows.size();
            for (OwnerRef owner : owners) {
                Long uid = owner.userId();
                if (uid != null && !ownerFilter.isEmpty() && !ownerFilter.contains(uid)) {
                    continue;
                }
                long ownerKey = uid == null ? 0L : uid;
                OwnerJobKey key = new OwnerJobKey(reqId, ownerKey);
                List<HrKpiAssessmentVO.TaskRow> kpiRows = kpiByKey.get(key);
                HrKpiAssessmentVO.TaskRow row = (kpiRows == null || kpiRows.isEmpty())
                        ? syntheticTaskRow(reqId, jobName, owner, rule, monthFrom, defaultDue)
                        : mergeKpiTaskRow(pickBestKpiRow(kpiRows), reqId, jobName, owner, rule);
                applyMonthAttainment(row, rule, attainment);
                rows.add(row);
            }
            if (rows.size() == rowsBefore && ownerFilter.isEmpty()) {
                HrKpiAssessmentVO.TaskRow row =
                        syntheticTaskRow(reqId, jobName, owners.get(0), rule, monthFrom, defaultDue);
                applyMonthAttainment(row, rule, attainment);
                rows.add(row);
            }
        }
        dedupeTasksToOneScorePerJob(rows);
        return rows;
    }

    /**
     * 按核算月入职/合格简历重算完成态：未达则回滚误标 DONE；已达则补记 DONE。
     */
    private void reevaluateCompletionForSettleMonth(
            YearMonth settleYm,
            List<HrRequisitionMonthTaskService.ActiveAssessmentTask> activeTasks,
            LocalDateTime monthFrom,
            LocalDateTime monthTo) {
        if (settleYm == null || activeTasks == null || activeTasks.isEmpty()) {
            return;
        }
        for (HrRequisitionMonthTaskService.ActiveAssessmentTask item : activeTasks) {
            MonthTaskRule rule = item.rule();
            Long reqId = rule.requisitionId();
            MonthAttainment att = evaluateMonthAttainment(rule, reqId, monthFrom, monthTo);
            List<Long> owners = loadOwners(reqId);
            if (owners.isEmpty()) {
                continue;
            }
            for (Long ownerId : owners) {
                if (att.done()) {
                    Long openId = earliestOpenTaskInDueMonth(reqId, ownerId, monthFrom, monthTo);
                    if (openId != null) {
                        jdbc.update("UPDATE hr_kpi_task SET priority_code = ? WHERE id = ?",
                                rule.priorityCode(), openId);
                        markDone(openId, att.doneAt() == null ? LocalDateTime.now() : att.doneAt(),
                                att.reason(), att.applicationId(), rule.score());
                    }
                } else {
                    reopenFalseDoneInDueMonth(reqId, ownerId, monthFrom, monthTo);
                }
            }
        }
    }

    private void reopenFalseDoneInDueMonth(Long requisitionId, Long ownerUserId,
                                           LocalDateTime monthFrom, LocalDateTime monthTo) {
        jdbc.update("""
                UPDATE hr_kpi_task
                SET status = 'OPEN', done_at = NULL, done_reason = NULL, done_application_id = NULL,
                    score = 0, update_time = NOW()
                WHERE requisition_id = ? AND owner_user_id = ? AND is_active = 1
                  AND status = 'DONE'
                  AND due_at >= ? AND due_at < ?
                """, requisitionId, ownerUserId, monthFrom, monthTo);
    }

    private Long earliestOpenTaskInDueMonth(Long requisitionId, Long ownerUserId,
                                            LocalDateTime monthFrom, LocalDateTime monthTo) {
        return jdbc.query("""
                SELECT id FROM hr_kpi_task
                WHERE requisition_id = ? AND owner_user_id = ? AND status = 'OPEN' AND is_active = 1
                  AND due_at >= ? AND due_at < ?
                ORDER BY slot_no, id LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, requisitionId, ownerUserId, monthFrom, monthTo);
    }

    /** 核算月达标判定：入职、合格简历均只统计 [monthFrom, monthTo)。 */
    private MonthAttainment evaluateMonthAttainment(
            MonthTaskRule rule, Long requisitionId, LocalDateTime monthFrom, LocalDateTime monthTo) {
        if (rule == null || requisitionId == null) {
            return MonthAttainment.notDone();
        }
        if (rule.allowsOnboard()) {
            List<OnboardHit> onboards = listOnboards(requisitionId).stream()
                    .filter(h -> !h.at().isBefore(monthFrom) && h.at().isBefore(monthTo))
                    .toList();
            if (onboards.size() >= rule.onboardTarget()) {
                OnboardHit hit = onboards.get(rule.onboardTarget() - 1);
                return new MonthAttainment(true, "ONBOARD", hit.at(), hit.applicationId());
            }
        }
        if (rule.allowsResume()) {
            List<LocalDateTime> resumes = listQualifiedAts(requisitionId).stream()
                    .filter(at -> !at.isBefore(monthFrom) && at.isBefore(monthTo))
                    .toList();
            if (resumes.size() >= rule.resumeTarget()) {
                return new MonthAttainment(true, "QUALIFIED_RESUME",
                        resumes.get(rule.resumeTarget() - 1), null);
            }
        }
        return MonthAttainment.notDone();
    }

    private void applyMonthAttainment(
            HrKpiAssessmentVO.TaskRow row, MonthTaskRule rule, MonthAttainment attainment) {
        double weight = rule == null ? 0.0 : rule.score();
        row.setWeight(weight);
        if (attainment != null && attainment.done()) {
            row.setStatus("DONE");
            row.setDoneReason(attainment.reason());
            row.setDoneAt(attainment.doneAt());
            row.setEarnedWeight(weight);
        } else {
            row.setStatus("OPEN");
            row.setDoneReason(null);
            row.setDoneAt(null);
            row.setEarnedWeight(0.0);
        }
    }

    private record MonthAttainment(boolean done, String reason, LocalDateTime doneAt, Long applicationId) {
        static MonthAttainment notDone() {
            return new MonthAttainment(false, null, null, null);
        }
    }

    private Map<Long, List<OwnerRef>> loadRequisitionOwnerRefs(List<Long> reqIds) {
        Map<Long, List<OwnerRef>> map = new LinkedHashMap<>();
        if (reqIds.isEmpty()) {
            return map;
        }
        String in = placeholders(reqIds.size());
        jdbc.query("""
                SELECT o.requisition_id, o.user_id, COALESCE(u.nickname, o.alias) AS owner_name
                FROM hr_requisition_owner o
                LEFT JOIN sys_user u ON u.user_id = o.user_id
                WHERE o.is_active = 1 AND o.user_id IS NOT NULL AND o.requisition_id IN (""" + in + """
                )
                ORDER BY o.requisition_id, o.sort_no, o.id
                """, (ResultSetExtractor<Void>) rs -> {
            // 必须显式 ResultSetExtractor：void lambda 会被当成 RowCallbackHandler，再 while(next) 会跳过首行
            while (rs.next()) {
                long reqId = rs.getLong("requisition_id");
                map.computeIfAbsent(reqId, k -> new ArrayList<>())
                        .add(new OwnerRef(rs.getLong("user_id"), rs.getString("owner_name")));
            }
            return null;
        }, reqIds.toArray());
        return map;
    }

    private Map<OwnerJobKey, List<HrKpiAssessmentVO.TaskRow>> loadKpiTaskRowsByOwnerJob(
            List<Long> reqIds, LocalDateTime monthFrom, LocalDateTime monthTo, boolean settleCurrentMonth) {
        Map<OwnerJobKey, List<HrKpiAssessmentVO.TaskRow>> map = new LinkedHashMap<>();
        if (reqIds.isEmpty()) {
            return map;
        }
        String in = placeholders(reqIds.size());
        StringBuilder sql = new StringBuilder("""
                SELECT t.id, t.requisition_id, t.owner_user_id, u.nickname owner_name, t.slot_no,
                       t.priority_code, t.published_at, t.due_at, t.origin_due_at, t.status, t.done_at,
                       t.done_reason, t.score
                FROM hr_kpi_task t
                LEFT JOIN sys_user u ON u.user_id = t.owner_user_id
                WHERE t.is_active = 1 AND t.status IN ('OPEN','DONE')
                  AND t.requisition_id IN (""" + in + """
                )
                  AND (
                    (t.due_at >= ? AND t.due_at < ?)
                """);
        List<Object> args = new ArrayList<>(reqIds);
        args.add(monthFrom);
        args.add(monthTo);
        if (settleCurrentMonth) {
            sql.append(" OR t.status = 'OPEN' ");
        }
        sql.append(") ORDER BY t.requisition_id, t.owner_user_id, t.due_at, t.id");
        jdbc.query(sql.toString(), (ResultSetExtractor<Void>) rs -> {
            while (rs.next()) {
                HrKpiAssessmentVO.TaskRow row = new HrKpiAssessmentVO.TaskRow();
                long reqId = rs.getLong("requisition_id");
                long ownerUserIdRaw = rs.getLong("owner_user_id");
                Long ownerUserId = rs.wasNull() ? null : ownerUserIdRaw;
                long ownerKey = ownerUserId == null ? 0L : ownerUserId;
                row.setId(rs.getLong("id"));
                row.setRequisitionId(reqId);
                row.setOwnerUserId(ownerUserId);
                row.setOwnerName(rs.getString("owner_name"));
                row.setSlotNo(rs.getInt("slot_no"));
                row.setPriorityCode(rs.getString("priority_code"));
                row.setPublishedAt(rs.getTimestamp("published_at").toLocalDateTime());
                row.setDueAt(rs.getTimestamp("due_at").toLocalDateTime());
                row.setOriginDueAt(rs.getTimestamp("origin_due_at").toLocalDateTime());
                row.setStatus(rs.getString("status"));
                row.setDoneAt(rs.getTimestamp("done_at") == null ? null : rs.getTimestamp("done_at").toLocalDateTime());
                row.setDoneReason(rs.getString("done_reason"));
                row.setScore(rs.getInt("score"));
                map.computeIfAbsent(new OwnerJobKey(reqId, ownerKey), k -> new ArrayList<>()).add(row);
            }
            return null;
        }, args.toArray());
        return map;
    }

    private HrKpiAssessmentVO.TaskRow mergeKpiTaskRow(
            HrKpiAssessmentVO.TaskRow row, Long requisitionId, String jobName, OwnerRef owner, MonthTaskRule rule) {
        row.setRequisitionId(requisitionId);
        row.setJobName(jobName);
        if (owner.userId() != null) {
            row.setOwnerUserId(owner.userId());
        }
        if (StringUtils.hasText(owner.nickname())) {
            row.setOwnerName(owner.nickname());
        }
        row.setPriorityCode(rule.priorityCode());
        // 完成态由 applyMonthAttainment 按核算月入职/简历重算，不信任历史 done_* 脏数据
        return row;
    }

    private HrKpiAssessmentVO.TaskRow syntheticTaskRow(
            Long requisitionId, String jobName, OwnerRef owner, MonthTaskRule rule,
            LocalDateTime monthFrom, LocalDateTime defaultDue) {
        HrKpiAssessmentVO.TaskRow row = new HrKpiAssessmentVO.TaskRow();
        Long uid = owner.userId();
        row.setId(syntheticTaskId(requisitionId, uid));
        row.setRequisitionId(requisitionId);
        row.setJobName(jobName);
        row.setOwnerUserId(uid);
        row.setOwnerName(owner.nickname());
        row.setSlotNo(1);
        row.setPriorityCode(rule.priorityCode());
        row.setPublishedAt(monthFrom);
        row.setDueAt(defaultDue);
        row.setOriginDueAt(defaultDue);
        row.setStatus("OPEN");
        row.setScore(0);
        double weight = rule.score();
        row.setWeight(weight);
        row.setEarnedWeight(0.0);
        return row;
    }

    private static long syntheticTaskId(long requisitionId, Long ownerUserId) {
        long o = ownerUserId == null ? 0L : ownerUserId;
        return -(requisitionId * 1_000_000L + o);
    }

    private HrKpiAssessmentVO.TaskRow pickBestKpiRow(List<HrKpiAssessmentVO.TaskRow> list) {
        if (list.size() == 1) {
            return list.get(0);
        }
        return list.stream()
                .min(Comparator
                        .comparing((HrKpiAssessmentVO.TaskRow t) -> !"DONE".equals(t.getStatus()))
                        .thenComparing(HrKpiAssessmentVO.TaskRow::getSlotNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(HrKpiAssessmentVO.TaskRow::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(list.get(0));
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private record OwnerRef(Long userId, String nickname) {
    }

    private record OwnerJobKey(long requisitionId, long ownerUserId) {
    }

    /**
     * 同一负责人×岗位只计 1 条：优先保留 DONE；多条 DONE 只留一条满分。
     */
    private void dedupeTasksToOneScorePerJob(List<HrKpiAssessmentVO.TaskRow> tasks) {
        if (tasks == null || tasks.size() <= 1) {
            return;
        }
        record Key(long requisitionId, long ownerUserId) {
        }
        Map<Key, List<HrKpiAssessmentVO.TaskRow>> groups = new LinkedHashMap<>();
        List<HrKpiAssessmentVO.TaskRow> keep = new ArrayList<>();
        for (HrKpiAssessmentVO.TaskRow t : tasks) {
            Long reqId = t.getRequisitionId();
            if (reqId == null) {
                keep.add(t);
                continue;
            }
            long ownerKey = t.getOwnerUserId() == null ? 0L : t.getOwnerUserId();
            groups.computeIfAbsent(new Key(reqId, ownerKey), k -> new ArrayList<>()).add(t);
        }
        for (List<HrKpiAssessmentVO.TaskRow> list : groups.values()) {
            if (list.size() == 1) {
                keep.add(list.get(0));
                continue;
            }
            list.sort(Comparator
                    .comparing((HrKpiAssessmentVO.TaskRow t) -> !"DONE".equals(t.getStatus()))
                    .thenComparing(HrKpiAssessmentVO.TaskRow::getSlotNo, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(HrKpiAssessmentVO.TaskRow::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            HrKpiAssessmentVO.TaskRow first = list.get(0);
            double weight = first.getWeight() == null ? 0.0 : first.getWeight();
            if ("DONE".equals(first.getStatus())) {
                first.setEarnedWeight(weight);
            } else {
                first.setEarnedWeight(0.0);
            }
            keep.add(first);
        }
        tasks.clear();
        tasks.addAll(keep);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /** @deprecated 统一计分规则已废弃，改按岗位按月任务要求 */
    @Deprecated
    public HrKpiScoreRuleVO getScoreRule() {
        throw new BusinessException("统一考核计分规则已停用，请在招聘需求中按岗位配置每月任务要求");
    }

    /** @deprecated 统一计分规则已废弃 */
    @Deprecated
    @Transactional
    public void saveScoreRule(HrKpiScoreRuleDTO dto) {
        throw new BusinessException("统一考核计分规则已停用，请在招聘需求中按岗位配置每月任务要求");
    }

    /**
     * 入职达成：当月入职人数达门槛则完成（P0 或 P1）；每月只得一次分。
     */
    private void tryCompleteByOnboard(Long requisitionId, Long ownerUserId, LocalDateTime at, Long applicationId) {
        Long taskId = earliestOpenTask(requisitionId, ownerUserId);
        if (taskId == null) {
            return;
        }
        Map<String, Object> task = loadTask(taskId);
        if (task == null) {
            return;
        }
        LocalDateTime dueAt = (LocalDateTime) task.get("dueAt");
        YearMonth ym = dueAt == null ? YearMonth.now() : YearMonth.from(dueAt);
        LocalDateTime monthFrom = ym.atDay(1).atStartOfDay();
        LocalDateTime monthTo = ym.plusMonths(1).atDay(1).atStartOfDay();
        if (countDoneInDueMonth(requisitionId, ownerUserId, monthFrom, monthTo) > 0) {
            return;
        }
        MonthTaskRule rule = monthTaskService.loadRule(requisitionId, ym.toString());
        if (rule == null) {
            return;
        }
        MonthAttainment att = evaluateMonthAttainment(rule, requisitionId, monthFrom, monthTo);
        if (!att.done() || !"ONBOARD".equals(att.reason())) {
            return;
        }
        jdbc.update("UPDATE hr_kpi_task SET priority_code = ? WHERE id = ?", rule.priorityCode(), taskId);
        LocalDateTime doneAt = att.doneAt() != null ? att.doneAt() : at;
        Long appId = applicationId != null ? applicationId : att.applicationId();
        markDone(taskId, doneAt, "ONBOARD", appId, rule.score());
    }

    /**
     * 合格简历达成：当月份数达门槛则完成（仅 P1 且配置了简历门槛）；与入职为「或」，先达标者记分。
     */
    private void tryCompleteByQualifiedResumes(Long requisitionId, Long ownerUserId) {
        Long taskId = earliestOpenTask(requisitionId, ownerUserId);
        if (taskId == null) {
            return;
        }
        Map<String, Object> task = loadTask(taskId);
        if (task == null) {
            return;
        }
        LocalDateTime dueAt = (LocalDateTime) task.get("dueAt");
        YearMonth ym = dueAt == null ? YearMonth.now() : YearMonth.from(dueAt);
        LocalDateTime monthFrom = ym.atDay(1).atStartOfDay();
        LocalDateTime monthTo = ym.plusMonths(1).atDay(1).atStartOfDay();
        if (countDoneInDueMonth(requisitionId, ownerUserId, monthFrom, monthTo) > 0) {
            return;
        }
        MonthTaskRule rule = monthTaskService.loadRule(requisitionId, ym.toString());
        if (rule == null || !rule.allowsResume()) {
            return;
        }
        MonthAttainment att = evaluateMonthAttainment(rule, requisitionId, monthFrom, monthTo);
        if (!att.done() || !"QUALIFIED_RESUME".equals(att.reason())) {
            return;
        }
        jdbc.update("UPDATE hr_kpi_task SET priority_code = ? WHERE id = ?", rule.priorityCode(), taskId);
        markDone(taskId, att.doneAt() == null ? LocalDateTime.now() : att.doneAt(),
                "QUALIFIED_RESUME", null, rule.score());
    }

    private Long earliestOpenTask(Long requisitionId, Long ownerUserId) {
        return jdbc.query("""
                SELECT id FROM hr_kpi_task
                WHERE requisition_id = ? AND owner_user_id = ? AND status = 'OPEN' AND is_active = 1
                ORDER BY slot_no LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, requisitionId, ownerUserId);
    }

    private int countDoneInDueMonth(Long requisitionId, Long ownerUserId,
                                    LocalDateTime monthFrom, LocalDateTime monthTo) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_kpi_task
                WHERE requisition_id = ? AND owner_user_id = ? AND status = 'DONE' AND is_active = 1
                  AND due_at >= ? AND due_at < ?
                """, Integer.class, requisitionId, ownerUserId, monthFrom, monthTo);
        return n == null ? 0 : n;
    }

    private List<Long> listOpenTaskIdsInDueMonth(Long requisitionId, Long ownerUserId,
                                                 LocalDateTime monthFrom, LocalDateTime monthTo) {
        return jdbc.query("""
                SELECT id FROM hr_kpi_task
                WHERE requisition_id = ? AND owner_user_id = ? AND status = 'OPEN' AND is_active = 1
                  AND due_at >= ? AND due_at < ?
                ORDER BY slot_no, id
                """, (rs, i) -> rs.getLong(1), requisitionId, ownerUserId, monthFrom, monthTo);
    }

    private void cancelOpenTasksInDueMonth(Long requisitionId, Long ownerUserId,
                                           LocalDateTime monthFrom, LocalDateTime monthTo) {
        jdbc.update("""
                UPDATE hr_kpi_task SET status = 'CANCELLED', update_time = NOW()
                WHERE requisition_id = ? AND owner_user_id = ? AND status = 'OPEN' AND is_active = 1
                  AND due_at >= ? AND due_at < ?
                """, requisitionId, ownerUserId, monthFrom, monthTo);
    }

    private int countOnboardedAppsInRange(Long requisitionId, LocalDateTime from, LocalDateTime to) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_application a
                WHERE a.requisition_id = ? AND a.is_active = 1 AND a.current_stage = 'ONBOARDED'
                  AND COALESCE(
                        (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.stage_code = 'ONBOARDED' AND e.is_active = 1),
                        a.update_time, a.create_time
                      ) >= ?
                  AND COALESCE(
                        (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.stage_code = 'ONBOARDED' AND e.is_active = 1),
                        a.update_time, a.create_time
                      ) < ?
                """, Integer.class, requisitionId, from, to);
        return n == null ? 0 : n;
    }

    private void markDone(Long taskId, LocalDateTime doneAt, String reason, Long applicationId, int score) {
        jdbc.update("""
                UPDATE hr_kpi_task
                SET status = 'DONE', done_at = ?, done_reason = ?, done_application_id = ?, score = ?, update_time = NOW()
                WHERE id = ? AND status = 'OPEN' AND is_active = 1
                """, doneAt, reason, applicationId, score, taskId);
    }

    /**
     * 统计合格简历份数（半开区间 [from, to)）；已入职人选排除。
     */
    private int countQualifiedResumes(Long requisitionId, LocalDateTime from, LocalDateTime to) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT rec.application_id)
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                WHERE a.requisition_id = ? AND rec.is_active = 1 AND rec.resume_qualified = 1
                  AND a.current_stage <> 'ONBOARDED'
                  AND COALESCE(rec.interviewed_at, rec.update_time, rec.create_time) >= ?
                  AND COALESCE(rec.interviewed_at, rec.update_time, rec.create_time) < ?
                """, Integer.class, requisitionId, from, to);
        return n == null ? 0 : n;
    }

    private LocalDateTime onboardEventAt(Long applicationId) {
        return jdbc.query("""
                SELECT event_at FROM hr_stage_event
                WHERE application_id = ? AND stage_code = 'ONBOARDED' AND is_active = 1
                ORDER BY event_at ASC LIMIT 1
                """, rs -> rs.next() && rs.getTimestamp(1) != null
                ? rs.getTimestamp(1).toLocalDateTime() : null, applicationId);
    }

    private Map<String, Object> loadTask(Long id) {
        return jdbc.query("""
                SELECT id, priority_code, published_at, due_at FROM hr_kpi_task WHERE id = ? AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("priorityCode", rs.getString("priority_code"));
            m.put("publishedAt", rs.getTimestamp("published_at").toLocalDateTime());
            m.put("dueAt", rs.getTimestamp("due_at").toLocalDateTime());
            return m;
        }, id);
    }

    private List<Long> loadOwners(Long requisitionId) {
        return jdbc.query("""
                SELECT user_id FROM hr_requisition_owner
                WHERE requisition_id = ? AND is_active = 1 AND user_id IS NOT NULL
                ORDER BY sort_no, id
                """, (rs, i) -> rs.getLong(1), requisitionId);
    }

    private int countAnyTask(Long requisitionId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_kpi_task WHERE requisition_id = ? AND is_active = 1
                """, Integer.class, requisitionId);
        return n == null ? 0 : n;
    }

    private int countSlots(Long requisitionId, Long ownerId, String status) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_kpi_task
                WHERE requisition_id = ? AND owner_user_id = ? AND status = ? AND is_active = 1
                """, Integer.class, requisitionId, ownerId, status);
        return n == null ? 0 : n;
    }

    private List<Integer> listSlots(Long requisitionId, Long ownerId, String status) {
        return jdbc.query("""
                SELECT slot_no FROM hr_kpi_task
                WHERE requisition_id = ? AND owner_user_id = ? AND status = ? AND is_active = 1
                ORDER BY slot_no
                """, (rs, i) -> rs.getInt(1), requisitionId, ownerId, status);
    }

    private int maxSlot(Long requisitionId, Long ownerId) {
        Integer n = jdbc.queryForObject("""
                SELECT COALESCE(MAX(slot_no), 0) FROM hr_kpi_task
                WHERE requisition_id = ? AND owner_user_id = ? AND is_active = 1
                """, Integer.class, requisitionId, ownerId);
        return n == null ? 0 : n;
    }

    private static String safeUser() {
        try {
            String u = SecurityUtils.getCurrentUsername();
            return u == null ? "system" : u;
        } catch (Exception ex) {
            return "system";
        }
    }
}
