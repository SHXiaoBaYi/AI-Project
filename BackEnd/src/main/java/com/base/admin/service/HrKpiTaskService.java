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

    /** 需求保存/改状态/改负责人或 HC 后同步任务槽位。 */
    @Transactional
    public void syncForRequisition(Long requisitionId) {
        if (requisitionId == null) {
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
        String currentMonth = YearMonth.now().toString();
        MonthTaskRule monthRule = monthTaskService.loadRule(requisitionId, currentMonth);
        // 优先用当月任务要求的 P0/P1；未配置则回退岗位优先级（仅用于槽位，计分仍看月度要求）
        String priority = monthRule != null ? monthRule.priorityCode()
                : (req.get("priority") == null ? "" : String.valueOf(req.get("priority")).trim().toUpperCase());
        int headcount = Math.max(1, ((Number) req.get("headcount")).intValue());
        List<Long> owners = loadOwners(requisitionId);

        if (!"OPEN".equals(status) || !SCORE_PRIORITIES.contains(priority) || owners.isEmpty() || monthRule == null) {
            jdbc.update("""
                    UPDATE hr_kpi_task SET status = 'CANCELLED', update_time = NOW()
                    WHERE requisition_id = ? AND status = 'OPEN' AND is_active = 1
                    """, requisitionId);
            return;
        }

        // 按月核算：当期 OPEN 任务到期=当月末；发布日=岗位收到日（无则创建日）
        LocalDate publishDay = req.get("receivedDate") instanceof LocalDate d ? d : null;
        if (publishDay == null && req.get("createTime") instanceof LocalDateTime ct) {
            publishDay = ct.toLocalDate();
        }
        if (publishDay == null) {
            publishDay = LocalDate.now();
        }
        LocalDateTime published = publishDay.atStartOfDay();
        LocalDateTime due = endOfMonth(YearMonth.now().atDay(1).atStartOfDay());
        String username = safeUser();
        for (Long ownerId : owners) {
            int doneSlots = countSlots(requisitionId, ownerId, "DONE");
            int needOpen = Math.max(0, headcount - doneSlots);
            List<Integer> openSlots = listSlots(requisitionId, ownerId, "OPEN");
            // 补足 OPEN
            int nextSlot = maxSlot(requisitionId, ownerId) + 1;
            while (openSlots.size() < needOpen) {
                int slot = nextSlot++;
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
                openSlots.add(slot);
            }
            // 多余 OPEN 取消（从大 slot 开始）
            openSlots.sort((a, b) -> Integer.compare(b, a));
            while (openSlots.size() > needOpen) {
                int slot = openSlots.remove(0);
                jdbc.update("""
                        UPDATE hr_kpi_task SET status = 'CANCELLED', update_time = NOW()
                        WHERE requisition_id = ? AND owner_user_id = ? AND slot_no = ?
                          AND status = 'OPEN' AND is_active = 1
                        """, requisitionId, ownerId, slot);
            }
            // 刷新仍 OPEN 的优先级快照
            jdbc.update("""
                    UPDATE hr_kpi_task SET priority_code = ?, update_time = NOW()
                    WHERE requisition_id = ? AND owner_user_id = ? AND status = 'OPEN' AND is_active = 1
                    """, priority, requisitionId, ownerId);
        }
        // 按岗位发布日重算本需求下未延期任务的发布/到期（生产同步后也要纠偏）
        repairSchedulesForRequisition(requisitionId, published, due);
        // 已不在负责人列表中的 OPEN 任务取消
        if (!owners.isEmpty()) {
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
        List<Long> ids = jdbc.query("""
                SELECT id FROM hr_requisition
                WHERE is_active = 1 AND status = 'OPEN'
                  AND UPPER(IFNULL(job_priority_code, '')) IN ('P0', 'P1')
                ORDER BY id
                """, (rs, i) -> rs.getLong(1));
        int n = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            syncForRequisition(id);
            List<Long> owners = loadOwners(id);
            for (Long ownerId : owners) {
                tryCompleteByOnboard(id, ownerId, now, null);
                tryCompleteByQualifiedResumes(id, ownerId);
            }
            n++;
        }
        // 含已关闭岗位上的历史任务：生产同步后纠正「全员同一发布/到期」
        repairAllTaskSchedulesFromRequisitions();
        return n;
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
                SELECT t.requisition_id, t.settle_month, t.priority_code, t.achieve_mode, t.target_count, t.score,
                       r.headcount, COALESCE(r.received_date, DATE(r.create_time)) AS start_day
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
            int need = Math.max(1, ((Number) mt.get("target_count")).intValue());
            int score = Math.max(0, ((Number) mt.get("score")).intValue());
            int headcount = Math.max(1, ((Number) mt.get("headcount")).intValue());
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
            for (Long ownerId : owners) {
                int slot = maxSlot(requisitionId, ownerId) + 1;
                int written = 0;
                if (HrRequisitionMonthTaskService.MODE_RESUME.equals(mode)) {
                    int resumeCursor = 0;
                    while (resumeCursor + need <= qualifiedAts.size() && written < headcount) {
                        LocalDateTime doneAt = qualifiedAts.get(resumeCursor + need - 1);
                        insertHistoricalTask(requisitionId, ownerId, slot++, priority, published, due,
                                "DONE", doneAt, "QUALIFIED_RESUME", null, score);
                        written++;
                        resumeCursor += need;
                    }
                } else {
                    int onboardCursor = 0;
                    while (onboardCursor + need <= onboards.size() && written < headcount) {
                        OnboardHit last = onboards.get(onboardCursor + need - 1);
                        insertHistoricalTask(requisitionId, ownerId, slot++, priority, published, due,
                                "DONE", last.at(), "ONBOARD", last.applicationId(), score);
                        written++;
                        onboardCursor += need;
                    }
                }
                taskCount += written;
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
        // 增量同步在招岗位槽位；历史 DONE 由 backfillAllHistorical 生成
        syncAllOpenScoreRequisitions();
        HrKpiAssessmentQueryDTO q = query == null ? new HrKpiAssessmentQueryDTO() : query;
        LocalDate month = q.getSettleMonth() == null ? LocalDate.now() : q.getSettleMonth();
        LocalDate monthStart = month.withDayOfMonth(1);
        LocalDateTime from = monthStart.atStartOfDay();
        LocalDateTime to = monthStart.plusMonths(1).atStartOfDay();
        String settleMonthKey = YearMonth.from(monthStart).toString();
        Map<Long, MonthTaskRule> monthRules = monthTaskService.loadRulesForMonth(settleMonthKey);

        // 当前自然月：本月到期的 DONE + 全部仍 OPEN 的在招任务（负责人不能因到期纠偏到历史月而消失）
        // 历史月：仅按 due_at 落在该月的任务核算
        boolean settleCurrentMonth = monthStart.equals(LocalDate.now().withDayOfMonth(1));

        StringBuilder sql = new StringBuilder("""
                SELECT t.id, t.requisition_id, r.job_name, t.owner_user_id, u.nickname owner_name, t.slot_no,
                       t.priority_code, t.published_at, t.due_at, t.origin_due_at, t.status, t.done_at,
                       t.done_reason, t.score
                FROM hr_kpi_task t
                JOIN hr_requisition r ON r.id = t.requisition_id AND r.is_active = 1
                LEFT JOIN sys_user u ON u.user_id = t.owner_user_id
                WHERE t.is_active = 1 AND t.status IN ('OPEN','DONE')
                  AND (
                    (t.due_at >= ? AND t.due_at < ?)
                """);
        List<Object> args = new ArrayList<>();
        args.add(from);
        args.add(to);
        if (settleCurrentMonth) {
            sql.append(" OR t.status = 'OPEN' ");
        }
        sql.append(')');
        List<Long> ownerIds = q.getOwnerUserIds() == null ? List.of()
                : q.getOwnerUserIds().stream().filter(Objects::nonNull).distinct().toList();
        if (!ownerIds.isEmpty()) {
            sql.append(" AND t.owner_user_id IN (");
            for (int i = 0; i < ownerIds.size(); i++) {
                if (i > 0) {
                    sql.append(',');
                }
                sql.append('?');
                args.add(ownerIds.get(i));
            }
            sql.append(')');
        }
        sql.append(" ORDER BY t.owner_user_id, t.due_at, t.id");

        List<HrKpiAssessmentVO.TaskRow> allTasks = new ArrayList<>();
        jdbc.query(sql.toString(), rs -> {
            while (rs.next()) {
                HrKpiAssessmentVO.TaskRow row = new HrKpiAssessmentVO.TaskRow();
                long requisitionId = rs.getLong("requisition_id");
                row.setId(rs.getLong("id"));
                row.setRequisitionId(requisitionId);
                row.setJobName(rs.getString("job_name"));
                row.setOwnerUserId(rs.getLong("owner_user_id"));
                row.setOwnerName(rs.getString("owner_name"));
                row.setSlotNo(rs.getInt("slot_no"));
                MonthTaskRule mr = monthRules.get(requisitionId);
                String priority = mr != null ? mr.priorityCode() : rs.getString("priority_code");
                row.setPriorityCode(priority);
                row.setPublishedAt(rs.getTimestamp("published_at").toLocalDateTime());
                row.setDueAt(rs.getTimestamp("due_at").toLocalDateTime());
                row.setOriginDueAt(rs.getTimestamp("origin_due_at").toLocalDateTime());
                String status = rs.getString("status");
                row.setStatus(status);
                row.setDoneAt(rs.getTimestamp("done_at") == null ? null : rs.getTimestamp("done_at").toLocalDateTime());
                row.setDoneReason(rs.getString("done_reason"));
                row.setScore(rs.getInt("score"));
                double weight = mr != null ? mr.score() : 0.0;
                row.setWeight(weight);
                row.setEarnedWeight("DONE".equals(status) ? weight : 0.0);
                allTasks.add(row);
            }
        }, args.toArray());

        // 合格简历模式：未满门槛按份折算
        applyResumePartialScores(allTasks, monthRules);

        Map<Long, HrKpiAssessmentVO.OwnerSummary> owners = new LinkedHashMap<>();
        double overallTotalWeight = 0.0;
        double overallDoneWeight = 0.0;
        for (HrKpiAssessmentVO.TaskRow t : allTasks) {
            HrKpiAssessmentVO.OwnerSummary s = owners.computeIfAbsent(t.getOwnerUserId(), id -> {
                HrKpiAssessmentVO.OwnerSummary o = new HrKpiAssessmentVO.OwnerSummary();
                o.setOwnerUserId(id);
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
            overallTotalWeight += weight;
            boolean p0 = "P0".equalsIgnoreCase(t.getPriorityCode());
            if (p0) {
                s.setP0Due(s.getP0Due() + 1);
            } else {
                s.setP1Due(s.getP1Due() + 1);
            }
            if ("DONE".equals(t.getStatus())) {
                s.setDoneCount(s.getDoneCount() + 1);
                s.setDoneWeight(round2(s.getDoneWeight() + earned));
                overallDoneWeight += earned;
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
                if (earned > 0) {
                    s.setDoneWeight(round2(s.getDoneWeight() + earned));
                    overallDoneWeight += earned;
                    if (!p0) {
                        s.setP1ResumePartialScore(round2(
                                (s.getP1ResumePartialScore() == null ? 0.0 : s.getP1ResumePartialScore()) + earned));
                        MonthTaskRule mr = monthRules.get(t.getRequisitionId());
                        int need = mr != null ? mr.targetCount() : 1;
                        double full = mr != null ? mr.score() : 0;
                        double unit = partialResumeUnit(full, need);
                        int partialCnt = unit > 0 ? (int) Math.round(earned / unit) : 0;
                        s.setP1ResumePartialCount(
                                (s.getP1ResumePartialCount() == null ? 0 : s.getP1ResumePartialCount()) + partialCnt);
                    }
                }
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

        double overallRate = overallTotalWeight > 0 ? overallDoneWeight / overallTotalWeight : 0.0;
        int ownerCount = owners.size();
        double overallEarned = round2(sumOwnerEarned);
        double overallMax = round2(sumOwnerMax);

        String statusFilter = StringUtils.hasText(q.getStatus()) ? q.getStatus().trim().toUpperCase() : "";
        List<HrKpiAssessmentVO.TaskRow> tasks = statusFilter.isEmpty() ? allTasks
                : allTasks.stream().filter(t -> statusFilter.equalsIgnoreCase(t.getStatus())).toList();

        double avgP0 = monthRules.values().stream().filter(r -> "P0".equals(r.priorityCode()))
                .mapToInt(MonthTaskRule::score).average().orElse(0);
        double avgP1 = monthRules.values().stream().filter(r -> "P1".equals(r.priorityCode()))
                .mapToInt(MonthTaskRule::score).average().orElse(0);
        int sampleResumeNeed = monthRules.values().stream().filter(MonthTaskRule::resumeMode)
                .mapToInt(MonthTaskRule::targetCount).findFirst().orElse(3);
        int sampleOnboardNeed = monthRules.values().stream().filter(MonthTaskRule::onboardMode)
                .mapToInt(MonthTaskRule::targetCount).findFirst().orElse(1);

        HrKpiAssessmentVO vo = new HrKpiAssessmentVO();
        vo.setSettleMonth(monthStart);
        // baseScore 改为「本月任务总分」口径：个人展示 已得分/应得分
        vo.setBaseScore(ownerCount > 0 ? round2(overallMax / Math.max(ownerCount, 1)) : ASSESS_BASE_SCORE_DEFAULT);
        vo.setWeightP0(round2(avgP0));
        vo.setWeightP1(round2(avgP1));
        vo.setP0OnboardCount(sampleOnboardNeed);
        vo.setP1QualifiedResumeCount(sampleResumeNeed);
        vo.setP1OnboardCount(sampleOnboardNeed);
        vo.setOwnerCount(ownerCount);
        vo.setOverallTotalWeight(round2(overallTotalWeight));
        vo.setOverallDoneWeight(overallEarned);
        vo.setOverallCompletionRate(round4(overallRate));
        vo.setOverallScore(overallEarned);
        vo.setOverallScoreMax(overallMax);
        int configuredJobs = monthRules.size();
        vo.setScoreRuleHint(String.format(
                "%s 已配置 %d 个岗位的月度任务要求；按岗位当月 P0/P1 与「到岗或合格简历二选一」计分。"
                        + "未配置岗位本月不计分。合格简历未满门槛按份折算。",
                settleMonthKey, configuredJobs));
        vo.setOwners(new ArrayList<>(owners.values()));
        vo.setTasks(tasks);
        return vo;
    }

    /**
     * 对每个「需求+负责人」最早的 OPEN 且当月为合格简历模式的任务：按未消耗份数写入折算分。
     */
    private void applyResumePartialScores(List<HrKpiAssessmentVO.TaskRow> tasks,
                                          Map<Long, MonthTaskRule> monthRules) {
        if (tasks == null || tasks.isEmpty() || monthRules == null || monthRules.isEmpty()) {
            return;
        }
        record Key(long requisitionId, long ownerUserId) {
        }
        Map<Key, List<HrKpiAssessmentVO.TaskRow>> groups = new LinkedHashMap<>();
        for (HrKpiAssessmentVO.TaskRow t : tasks) {
            if (t.getRequisitionId() == null || t.getOwnerUserId() == null) {
                continue;
            }
            MonthTaskRule mr = monthRules.get(t.getRequisitionId());
            if (mr == null || !mr.resumeMode()) {
                continue;
            }
            groups.computeIfAbsent(new Key(t.getRequisitionId(), t.getOwnerUserId()), k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<Key, List<HrKpiAssessmentVO.TaskRow>> e : groups.entrySet()) {
            MonthTaskRule mr = monthRules.get(e.getKey().requisitionId());
            if (mr == null) {
                continue;
            }
            int need = mr.targetCount();
            double fullScore = mr.score();
            List<HrKpiAssessmentVO.TaskRow> list = e.getValue();
            list.sort(Comparator
                    .comparing(HrKpiAssessmentVO.TaskRow::getSlotNo, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(HrKpiAssessmentVO.TaskRow::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            HrKpiAssessmentVO.TaskRow open = list.stream()
                    .filter(t -> "OPEN".equals(t.getStatus()))
                    .findFirst()
                    .orElse(null);
            if (open == null || open.getPublishedAt() == null || open.getDueAt() == null) {
                continue;
            }
            int doneByResume = (int) list.stream()
                    .filter(t -> "DONE".equals(t.getStatus()) && "QUALIFIED_RESUME".equals(nz(t.getDoneReason())))
                    .count();
            int qualified = countQualifiedResumes(open.getRequisitionId(), open.getPublishedAt(), open.getDueAt());
            int progress = Math.max(0, qualified - doneByResume * need);
            if (progress <= 0) {
                continue;
            }
            if (progress >= need) {
                open.setEarnedWeight(round2(fullScore));
                continue;
            }
            open.setEarnedWeight(partialResumeScore(progress, need, fullScore));
        }
    }

    /** 未满门槛：前 need-1 份按截断到分位的单价累计；满门槛返回满分（补齐余数） */
    static double partialResumeScore(int resumes, int need, double fullScore) {
        if (resumes <= 0 || need <= 0 || fullScore <= 0) {
            return 0;
        }
        int n = Math.min(resumes, need);
        if (n >= need) {
            return round2(fullScore);
        }
        return round2(partialResumeUnit(fullScore, need) * n);
    }

    static double partialResumeUnit(double fullScore, int need) {
        if (need <= 0 || fullScore <= 0) {
            return 0;
        }
        // 1/3 → 0.33；满 3 份时用满分补齐余数（0.33*2+0.34=1）
        return Math.floor(fullScore / need * 100.0) / 100.0;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
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
     * 入职达成：按任务到期月的「月度任务要求」——仅当达成方式为到岗时生效。
     */
    private void tryCompleteByOnboard(Long requisitionId, Long ownerUserId, LocalDateTime at, Long applicationId) {
        int onboarded = countOnboardedApps(requisitionId);
        while (true) {
            Long taskId = earliestOpenTask(requisitionId, ownerUserId);
            if (taskId == null) {
                return;
            }
            Map<String, Object> task = loadTask(taskId);
            if (task == null) {
                return;
            }
            LocalDateTime dueAt = (LocalDateTime) task.get("dueAt");
            String settleMonth = dueAt == null ? YearMonth.now().toString() : YearMonth.from(dueAt).toString();
            MonthTaskRule rule = monthTaskService.loadRule(requisitionId, settleMonth);
            if (rule == null || rule.resumeMode()) {
                return;
            }
            int need = rule.targetCount();
            int score = rule.score();
            int doneByOnboard = countDoneByReason(requisitionId, ownerUserId, "ONBOARD");
            if (need <= 0 || onboarded / need <= doneByOnboard) {
                return;
            }
            // 刷新 OPEN 任务优先级快照
            jdbc.update("UPDATE hr_kpi_task SET priority_code = ? WHERE id = ?", rule.priorityCode(), taskId);
            markDone(taskId, at, "ONBOARD", applicationId, score);
        }
    }

    /** 合格简历模式：周期内份数达标则可完成（与到岗二选一，由月度任务要求决定）。 */
    private void tryCompleteByQualifiedResumes(Long requisitionId, Long ownerUserId) {
        while (true) {
            Long taskId = jdbc.query("""
                    SELECT id FROM hr_kpi_task
                    WHERE requisition_id = ? AND owner_user_id = ? AND status = 'OPEN' AND is_active = 1
                    ORDER BY slot_no LIMIT 1
                    """, rs -> rs.next() ? rs.getLong(1) : null, requisitionId, ownerUserId);
            if (taskId == null) {
                return;
            }
            Map<String, Object> task = loadTask(taskId);
            if (task == null) {
                return;
            }
            LocalDateTime publishedAt = (LocalDateTime) task.get("publishedAt");
            LocalDateTime dueAt = (LocalDateTime) task.get("dueAt");
            String settleMonth = dueAt == null ? YearMonth.now().toString() : YearMonth.from(dueAt).toString();
            MonthTaskRule rule = monthTaskService.loadRule(requisitionId, settleMonth);
            if (rule == null || !rule.resumeMode()) {
                return;
            }
            int qualified = countQualifiedResumes(requisitionId, publishedAt, dueAt);
            int need = rule.targetCount();
            int doneByResume = countDoneByReason(requisitionId, ownerUserId, "QUALIFIED_RESUME");
            if (need <= 0 || qualified / need <= doneByResume) {
                return;
            }
            jdbc.update("UPDATE hr_kpi_task SET priority_code = ? WHERE id = ?", rule.priorityCode(), taskId);
            markDone(taskId, LocalDateTime.now(), "QUALIFIED_RESUME", null, rule.score());
        }
    }

    private Long earliestOpenTask(Long requisitionId, Long ownerUserId) {
        return jdbc.query("""
                SELECT id FROM hr_kpi_task
                WHERE requisition_id = ? AND owner_user_id = ? AND status = 'OPEN' AND is_active = 1
                ORDER BY slot_no LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, requisitionId, ownerUserId);
    }

    private int countDoneByReason(Long requisitionId, Long ownerUserId, String reason) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_kpi_task
                WHERE requisition_id = ? AND owner_user_id = ? AND status = 'DONE' AND is_active = 1
                  AND done_reason = ?
                """, Integer.class, requisitionId, ownerUserId, reason);
        return n == null ? 0 : n;
    }

    private int countOnboardedApps(Long requisitionId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_application
                WHERE requisition_id = ? AND is_active = 1 AND current_stage = 'ONBOARDED'
                """, Integer.class, requisitionId);
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
     * 统计合格简历份数；已入职人选排除（P1 入职已单独记分，简历路径不再重复计）。
     */
    private int countQualifiedResumes(Long requisitionId, LocalDateTime from, LocalDateTime to) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT rec.application_id)
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                WHERE a.requisition_id = ? AND rec.is_active = 1 AND rec.resume_qualified = 1
                  AND a.current_stage <> 'ONBOARDED'
                  AND COALESCE(rec.interviewed_at, rec.update_time, rec.create_time) >= ?
                  AND COALESCE(rec.interviewed_at, rec.update_time, rec.create_time) <= ?
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
