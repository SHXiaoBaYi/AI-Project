package com.base.admin.service;

import com.base.admin.domain.dto.HrKpiAssessmentQueryDTO;
import com.base.admin.domain.dto.HrKpiScoreRuleDTO;
import com.base.admin.domain.vo.HrKpiAssessmentVO;
import com.base.admin.domain.vo.HrKpiScoreRuleVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * P0/P1 考核任务：OPEN 时按负责人×HC 生成；按到期月核算；入职/合格简历自动完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrKpiTaskService {

    private static final Set<String> SCORE_PRIORITIES = Set.of("P0", "P1");

    private final JdbcTemplate jdbc;

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
        String priority = req.get("priority") == null ? "" : String.valueOf(req.get("priority")).trim().toUpperCase();
        int headcount = Math.max(1, ((Number) req.get("headcount")).intValue());
        List<Long> owners = loadOwners(requisitionId);

        if (!"OPEN".equals(status) || !SCORE_PRIORITIES.contains(priority) || owners.isEmpty()) {
            jdbc.update("""
                    UPDATE hr_kpi_task SET status = 'CANCELLED', update_time = NOW()
                    WHERE requisition_id = ? AND status = 'OPEN' AND is_active = 1
                    """, requisitionId);
            return;
        }

        ScoreRule rule = loadScoreRule();
        int dueMonths = Math.max(1, rule.dueMonths());
        // 发布日=岗位收到日（无则创建日）；到期=发布日 + 规则周期 所在月末。禁止全员写死「本月末」。
        LocalDate publishDay = req.get("receivedDate") instanceof LocalDate d ? d : null;
        if (publishDay == null && req.get("createTime") instanceof LocalDateTime ct) {
            publishDay = ct.toLocalDate();
        }
        if (publishDay == null) {
            publishDay = LocalDate.now();
        }
        LocalDateTime published = publishDay.atStartOfDay();
        LocalDateTime due = endOfMonth(publishDay.plusMonths(dueMonths).atStartOfDay());
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
            // 刷新仍 OPEN 的优先级；未延期（due=origin_due）的按岗位发布日重算发布/到期，修复历史「全员本月末」脏数据
            jdbc.update("""
                    UPDATE hr_kpi_task
                    SET priority_code = ?,
                        published_at = ?,
                        due_at = IF(due_at <=> origin_due_at, ?, due_at),
                        origin_due_at = IF(due_at <=> origin_due_at, ?, origin_due_at),
                        update_time = NOW()
                    WHERE requisition_id = ? AND owner_user_id = ? AND status = 'OPEN' AND is_active = 1
                    """, priority, published, due, due, requisitionId, ownerId);
        }
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

    /** 面试评价写入合格简历后，尝试按 P1 规则完成任务。 */
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
        return n;
    }

    /**
     * 全量历史回填：按入职时间线与合格简历重建所有 P0/P1 考核任务（含已关闭岗位），
     * 完成任务的到期日落在完成当月，便于按月核算历史得分。
     */
    @Transactional
    public int backfillAllHistorical() {
        int cleared = jdbc.update("UPDATE hr_kpi_task SET is_active = 0 WHERE is_active = 1");
        log.info("考核任务历史回填：先软删旧任务 {} 条", cleared);
        ScoreRule rule = loadScoreRule();
        List<Map<String, Object>> reqs = jdbc.queryForList("""
                SELECT id, status, headcount, job_priority_code,
                       COALESCE(received_date, DATE(create_time)) AS start_day
                FROM hr_requisition
                WHERE is_active = 1
                  AND UPPER(IFNULL(job_priority_code, '')) IN ('P0', 'P1')
                ORDER BY id
                """);
        int reqCount = 0;
        int taskCount = 0;
        for (Map<String, Object> req : reqs) {
            Long requisitionId = ((Number) req.get("id")).longValue();
            String status = String.valueOf(req.get("status"));
            String priority = String.valueOf(req.get("job_priority_code")).trim().toUpperCase();
            int headcount = Math.max(1, ((Number) req.get("headcount")).intValue());
            List<Long> owners = loadOwners(requisitionId);
            if (owners.isEmpty()) {
                continue;
            }
            LocalDate startDay = req.get("start_day") instanceof java.sql.Date sd
                    ? sd.toLocalDate()
                    : (req.get("start_day") instanceof LocalDate ld ? ld : LocalDate.now());
            List<OnboardHit> onboards = listOnboards(requisitionId);
            List<LocalDateTime> qualifiedAts = "P1".equals(priority) ? listQualifiedAts(requisitionId) : List.of();
            for (Long ownerId : owners) {
                taskCount += backfillOwnerTasks(requisitionId, ownerId, priority, headcount,
                        rule, startDay, onboards, qualifiedAts);
            }
            reqCount++;
        }
        // 招聘中的再补齐当前 OPEN 槽
        syncAllOpenScoreRequisitions();
        log.info("考核任务历史回填完成：需求 {} 个，写入任务约 {} 条", reqCount, taskCount);
        return reqCount;
    }

    private int backfillOwnerTasks(Long requisitionId, Long ownerId, String priority,
                                   int headcount, ScoreRule rule, LocalDate startDay,
                                   List<OnboardHit> onboards, List<LocalDateTime> qualifiedAts) {
        int needOnboard = "P0".equals(priority) ? rule.p0OnboardCount() : rule.p1OnboardCount();
        int scoreOnboard = "P0".equals(priority) ? rule.p0Score() : rule.p1Score();
        int needResume = rule.p1QualifiedResumeCount();
        int scoreResume = rule.p1Score();
        int dueMonths = Math.max(1, rule.dueMonths());
        LocalDateTime published = startDay.atStartOfDay();
        int slot = 1;
        int written = 0;
        int onboardCursor = 0;
        int resumeCursor = 0;

        // 1) 按入职人数门槛生成历史 DONE：发布=岗位收到日；到期=完成所在月末（按月核算）
        while (onboardCursor + needOnboard <= onboards.size() && slot <= headcount) {
            OnboardHit last = onboards.get(onboardCursor + needOnboard - 1);
            LocalDateTime doneAt = last.at();
            LocalDateTime due = endOfMonth(doneAt);
            // 若完成早于「发布+周期」，仍保留岗位发布日；否则到期不早于规则周期末
            LocalDateTime ruleDue = endOfMonth(startDay.plusMonths(dueMonths).atStartOfDay());
            if (due.isBefore(ruleDue)) {
                due = ruleDue;
            }
            insertHistoricalTask(requisitionId, ownerId, slot, priority, published, due,
                    "DONE", doneAt, "ONBOARD", last.applicationId(), scoreOnboard);
            slot++;
            written++;
            onboardCursor += needOnboard;
        }

        // 2) P1：剩余 HC 用合格简历门槛补 DONE（与入职二选一）
        if ("P1".equals(priority)) {
            while (resumeCursor + needResume <= qualifiedAts.size() && slot <= headcount) {
                LocalDateTime doneAt = qualifiedAts.get(resumeCursor + needResume - 1);
                LocalDateTime due = endOfMonth(doneAt);
                LocalDateTime ruleDue = endOfMonth(startDay.plusMonths(dueMonths).atStartOfDay());
                if (due.isBefore(ruleDue)) {
                    due = ruleDue;
                }
                insertHistoricalTask(requisitionId, ownerId, slot, priority, published, due,
                        "DONE", doneAt, "QUALIFIED_RESUME", null, scoreResume);
                slot++;
                written++;
                resumeCursor += needResume;
            }
        }

        // 3) 仍在招：剩余槽位留给 sync 补 OPEN（此处不写，避免与当月回填策略冲突）
        return written;
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
        return jdbc.query("""
                SELECT MIN(COALESCE(rec.interviewed_at, rec.update_time, rec.create_time)) AS hit_at
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                WHERE a.requisition_id = ? AND rec.is_active = 1 AND rec.resume_qualified = 1
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

    /** 考核展示基准分：完成率×基准分=实得分（无额外加减分）。P0/P1 权重取自考核计分规则。 */
    public static final double ASSESS_BASE_SCORE = 30.0;

    public HrKpiAssessmentVO assessment(HrKpiAssessmentQueryDTO query) {
        // 增量同步在招岗位槽位；历史 DONE 由 backfillAllHistorical 生成
        syncAllOpenScoreRequisitions();
        HrKpiAssessmentQueryDTO q = query == null ? new HrKpiAssessmentQueryDTO() : query;
        LocalDate month = q.getSettleMonth() == null ? LocalDate.now() : q.getSettleMonth();
        LocalDate monthStart = month.withDayOfMonth(1);
        LocalDateTime from = monthStart.atStartOfDay();
        LocalDateTime to = monthStart.plusMonths(1).atStartOfDay();

        ScoreRule rule = loadScoreRule();
        double weightP0 = Math.max(0, rule.p0Score());
        double weightP1 = Math.max(0, rule.p1Score());

        StringBuilder sql = new StringBuilder("""
                SELECT t.id, t.requisition_id, r.job_name, t.owner_user_id, u.nickname owner_name, t.slot_no,
                       t.priority_code, t.published_at, t.due_at, t.origin_due_at, t.status, t.done_at,
                       t.done_reason, t.score
                FROM hr_kpi_task t
                JOIN hr_requisition r ON r.id = t.requisition_id AND r.is_active = 1
                LEFT JOIN sys_user u ON u.user_id = t.owner_user_id
                WHERE t.is_active = 1 AND t.status IN ('OPEN','DONE')
                  AND t.due_at >= ? AND t.due_at < ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(from);
        args.add(to);
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
        // 得分始终按本月全部到期任务核算；状态筛选仅作用于明细列表
        sql.append(" ORDER BY t.owner_user_id, t.due_at, t.id");

        List<HrKpiAssessmentVO.TaskRow> allTasks = new ArrayList<>();
        jdbc.query(sql.toString(), rs -> {
            while (rs.next()) {
                HrKpiAssessmentVO.TaskRow row = new HrKpiAssessmentVO.TaskRow();
                row.setId(rs.getLong("id"));
                row.setRequisitionId(rs.getLong("requisition_id"));
                row.setJobName(rs.getString("job_name"));
                row.setOwnerUserId(rs.getLong("owner_user_id"));
                row.setOwnerName(rs.getString("owner_name"));
                row.setSlotNo(rs.getInt("slot_no"));
                String priority = rs.getString("priority_code");
                row.setPriorityCode(priority);
                row.setPublishedAt(rs.getTimestamp("published_at").toLocalDateTime());
                row.setDueAt(rs.getTimestamp("due_at").toLocalDateTime());
                row.setOriginDueAt(rs.getTimestamp("origin_due_at").toLocalDateTime());
                String status = rs.getString("status");
                row.setStatus(status);
                row.setDoneAt(rs.getTimestamp("done_at") == null ? null : rs.getTimestamp("done_at").toLocalDateTime());
                row.setDoneReason(rs.getString("done_reason"));
                row.setScore(rs.getInt("score"));
                double weight = assessWeight(priority, weightP0, weightP1);
                row.setWeight(weight);
                row.setEarnedWeight("DONE".equals(status) ? weight : 0.0);
                allTasks.add(row);
            }
        }, args.toArray());

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
                o.setTotalWeight(0.0);
                o.setDoneWeight(0.0);
                o.setCompletionRate(0.0);
                o.setScore(0.0);
                return o;
            });
            double weight = t.getWeight() == null ? assessWeight(t.getPriorityCode(), weightP0, weightP1) : t.getWeight();
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
                s.setDoneWeight(round2(s.getDoneWeight() + weight));
                overallDoneWeight += weight;
                if (p0) {
                    s.setP0Done(s.getP0Done() + 1);
                } else {
                    s.setP1Done(s.getP1Done() + 1);
                }
            } else {
                s.setOpenCount(s.getOpenCount() + 1);
            }
        }
        for (HrKpiAssessmentVO.OwnerSummary s : owners.values()) {
            double total = s.getTotalWeight() == null ? 0.0 : s.getTotalWeight();
            double done = s.getDoneWeight() == null ? 0.0 : s.getDoneWeight();
            double rate = total > 0 ? done / total : 0.0;
            s.setCompletionRate(round4(rate));
            // 得分=整体完成率×30，无额外加减分
            s.setScore(round2(rate * ASSESS_BASE_SCORE));
        }

        double overallRate = overallTotalWeight > 0 ? overallDoneWeight / overallTotalWeight : 0.0;

        String statusFilter = StringUtils.hasText(q.getStatus()) ? q.getStatus().trim().toUpperCase() : "";
        List<HrKpiAssessmentVO.TaskRow> tasks = statusFilter.isEmpty() ? allTasks
                : allTasks.stream().filter(t -> statusFilter.equalsIgnoreCase(t.getStatus())).toList();

        HrKpiAssessmentVO vo = new HrKpiAssessmentVO();
        vo.setSettleMonth(monthStart);
        vo.setBaseScore(ASSESS_BASE_SCORE);
        vo.setWeightP0(weightP0);
        vo.setWeightP1(weightP1);
        vo.setOverallTotalWeight(round2(overallTotalWeight));
        vo.setOverallDoneWeight(round2(overallDoneWeight));
        vo.setOverallCompletionRate(round4(overallRate));
        vo.setOverallScore(round2(overallRate * ASSESS_BASE_SCORE));
        vo.setScoreRuleHint(String.format(
                "基准分%.0f分；得分=整体完成率×%.0f；P0权重%.1f、P1权重%.1f（取自考核计分规则）；完成计权重、未完成计0；无额外加减分",
                ASSESS_BASE_SCORE, ASSESS_BASE_SCORE, weightP0, weightP1));
        vo.setOwners(new ArrayList<>(owners.values()));
        vo.setTasks(tasks);
        return vo;
    }

    private static double assessWeight(String priorityCode, double weightP0, double weightP1) {
        return "P0".equalsIgnoreCase(priorityCode) ? weightP0 : weightP1;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    public HrKpiScoreRuleVO getScoreRule() {
        ScoreRule rule = loadScoreRule();
        HrKpiScoreRuleVO vo = new HrKpiScoreRuleVO();
        vo.setP0OnboardCount(rule.p0OnboardCount());
        vo.setP0Score(rule.p0Score());
        vo.setP1QualifiedResumeCount(rule.p1QualifiedResumeCount());
        vo.setP1OnboardCount(rule.p1OnboardCount());
        vo.setP1Score(rule.p1Score());
        vo.setDueMonths(rule.dueMonths());
        vo.setUpdateTime(rule.updateTime());
        return vo;
    }

    @Transactional
    public void saveScoreRule(HrKpiScoreRuleDTO dto) {
        if (dto == null) {
            throw new BusinessException("请填写计分规则");
        }
        jdbc.update("""
                INSERT INTO hr_kpi_score_rule (id, p0_onboard_count, p0_score, p1_qualified_resume_count,
                  p1_onboard_count, p1_score, due_months, update_by)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  p0_onboard_count = VALUES(p0_onboard_count),
                  p0_score = VALUES(p0_score),
                  p1_qualified_resume_count = VALUES(p1_qualified_resume_count),
                  p1_onboard_count = VALUES(p1_onboard_count),
                  p1_score = VALUES(p1_score),
                  due_months = VALUES(due_months),
                  update_by = VALUES(update_by)
                """, dto.getP0OnboardCount(), dto.getP0Score(), dto.getP1QualifiedResumeCount(),
                dto.getP1OnboardCount(), dto.getP1Score(), dto.getDueMonths(), safeUser());
    }

    /**
     * 入职达成：P0 按「完成 N 人入职记 S 分」；P1 按「周期内 M 人入职」与合格简历二选一。
     * 同一需求下已入职人数 / 门槛 = 可完成槽位数。
     */
    private void tryCompleteByOnboard(Long requisitionId, Long ownerUserId, LocalDateTime at, Long applicationId) {
        ScoreRule rule = loadScoreRule();
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
            boolean p0 = "P0".equalsIgnoreCase(String.valueOf(task.get("priorityCode")));
            int need = p0 ? rule.p0OnboardCount() : rule.p1OnboardCount();
            int score = p0 ? rule.p0Score() : rule.p1Score();
            int doneByOnboard = countDoneByReason(requisitionId, ownerUserId, "ONBOARD");
            if (need <= 0 || onboarded / need <= doneByOnboard) {
                return;
            }
            markDone(taskId, at, "ONBOARD", applicationId, score);
        }
    }

    /** P1：周期内合格简历份数达标则可完成（与入职二选一）。 */
    private void tryCompleteByQualifiedResumes(Long requisitionId, Long ownerUserId) {
        ScoreRule rule = loadScoreRule();
        while (true) {
            Long taskId = jdbc.query("""
                    SELECT id FROM hr_kpi_task
                    WHERE requisition_id = ? AND owner_user_id = ? AND status = 'OPEN' AND is_active = 1
                      AND UPPER(priority_code) = 'P1'
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
            int qualified = countQualifiedResumes(requisitionId, publishedAt, dueAt);
            int need = rule.p1QualifiedResumeCount();
            int doneByResume = countDoneByReason(requisitionId, ownerUserId, "QUALIFIED_RESUME");
            if (need <= 0 || qualified / need <= doneByResume) {
                return;
            }
            markDone(taskId, LocalDateTime.now(), "QUALIFIED_RESUME", null, rule.p1Score());
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

    private ScoreRule loadScoreRule() {
        ScoreRule row = jdbc.query("""
                SELECT p0_onboard_count, p0_score, p1_qualified_resume_count, p1_onboard_count,
                       p1_score, due_months, update_time
                FROM hr_kpi_score_rule WHERE id = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new ScoreRule(
                    Math.max(1, rs.getInt("p0_onboard_count")),
                    Math.max(0, rs.getInt("p0_score")),
                    Math.max(1, rs.getInt("p1_qualified_resume_count")),
                    Math.max(1, rs.getInt("p1_onboard_count")),
                    Math.max(0, rs.getInt("p1_score")),
                    Math.max(1, rs.getInt("due_months")),
                    rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime());
        });
        return row != null ? row : new ScoreRule(1, 2, 3, 1, 1, 1, null);
    }

    private record ScoreRule(int p0OnboardCount, int p0Score, int p1QualifiedResumeCount, int p1OnboardCount,
                             int p1Score, int dueMonths, LocalDateTime updateTime) {
    }

    private void markDone(Long taskId, LocalDateTime doneAt, String reason, Long applicationId, int score) {
        jdbc.update("""
                UPDATE hr_kpi_task
                SET status = 'DONE', done_at = ?, done_reason = ?, done_application_id = ?, score = ?, update_time = NOW()
                WHERE id = ? AND status = 'OPEN' AND is_active = 1
                """, doneAt, reason, applicationId, score, taskId);
    }

    private int countQualifiedResumes(Long requisitionId, LocalDateTime from, LocalDateTime to) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT rec.application_id)
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                WHERE a.requisition_id = ? AND rec.is_active = 1 AND rec.resume_qualified = 1
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
