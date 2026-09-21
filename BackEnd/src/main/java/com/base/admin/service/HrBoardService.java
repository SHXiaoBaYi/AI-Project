package com.base.admin.service;

import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.vo.HrBoardDrillVO;
import com.base.admin.domain.vo.HrBoardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HrBoardService {

    private static final FunnelStage[] FUNNEL = {
            new FunnelStage("SCREEN", "HR初筛通过", null),
            new FunnelStage("INVITE", "邀约成功", "邀约成功率"),
            new FunnelStage("SHOW", "候选人到面（一面）", "到面率"),
            new FunnelStage("RETEST", "复试通过", "复试通过率"),
            new FunnelStage("FINAL", "终面通过", "终面通过率"),
            new FunnelStage("SALARY", "薪资沟通中", "薪资沟通进入率"),
            new FunnelStage("BG_COLLECT", "背调资料收集中", "背调资料收集进入率"),
            new FunnelStage("BG_CHECK", "背调中", "背调进入率"),
            new FunnelStage("MEDICAL", "体检中", "体检进入率"),
            new FunnelStage("OFFER_PENDING", "待发offer", "待发Offer进入率"),
            new FunnelStage("OFFER", "发放Offer", "Offer发放率"),
            new FunnelStage("ACCEPT", "Offer接受", "Offer接受率"),
            new FunnelStage("ONBOARD", "候选人入职", "入职率")
    };

    private static final String[] CYCLE_STAGE_NAMES = {
            "需求发起至简历到位", "初筛到一面", "一面到复试", "终面到发Offer", "Offer发出到入职"
    };

    private static final Set<String> AT_OR_AFTER_FINAL = Set.of(
            "FINAL", "SALARY", "BG_COLLECT", "BG_CHECK", "MEDICAL", "OFFER_PENDING", "PENDING_ONBOARD",
            "OFFER_SENT", "OFFER_ACCEPTED", "ONBOARDED");
    private static final Set<String> AT_OR_AFTER_SALARY = Set.of(
            "SALARY", "BG_COLLECT", "BG_CHECK", "MEDICAL", "OFFER_PENDING", "PENDING_ONBOARD",
            "OFFER_SENT", "OFFER_ACCEPTED", "ONBOARDED");
    private static final Set<String> AT_OR_AFTER_BG_COLLECT = Set.of(
            "BG_COLLECT", "BG_CHECK", "MEDICAL", "OFFER_PENDING", "PENDING_ONBOARD",
            "OFFER_SENT", "OFFER_ACCEPTED", "ONBOARDED");
    private static final Set<String> AT_OR_AFTER_BG_CHECK = Set.of(
            "BG_CHECK", "MEDICAL", "OFFER_PENDING", "PENDING_ONBOARD",
            "OFFER_SENT", "OFFER_ACCEPTED", "ONBOARDED");
    private static final Set<String> AT_OR_AFTER_MEDICAL = Set.of(
            "MEDICAL", "OFFER_PENDING", "PENDING_ONBOARD", "OFFER_SENT", "OFFER_ACCEPTED", "ONBOARDED");
    private static final Set<String> AT_OR_AFTER_OFFER_PENDING = Set.of(
            "OFFER_PENDING", "PENDING_ONBOARD", "OFFER_SENT", "OFFER_ACCEPTED", "ONBOARDED");
    private static final Set<String> AT_OR_AFTER_OFFER_SENT = Set.of(
            "PENDING_ONBOARD", "OFFER_SENT", "OFFER_ACCEPTED", "ONBOARDED");

    private static final Set<String> RETEST_PASS = Set.of("SECOND_ROUND", "R3_PASS", "R4_PASS", "R5_PASS");

    private static final Set<String> SHOW_STAGES = Set.of(
            "SHOW_UP", "FIRST_ROUND", "FIRST_PENDING", "FIRST_FAIL", "R1_DISPUTE",
            "R2_PENDING", "R2_FAIL", "R2_DISPUTE", "R3_PENDING", "R3_FAIL", "R3_DISPUTE",
            "R4_PENDING", "R4_FAIL", "R4_DISPUTE", "R5_PENDING", "R5_FAIL", "R5_DISPUTE");

    private static final Pattern ISO_DATE = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern CN_DATE = Pattern.compile("(20\\d{2})?年?(\\d{1,2})月(\\d{1,2})日?");

    private final JdbcTemplate jdbc;
    private final HrDataScope dataScope;

    public HrBoardVO board(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        List<Milestone> rows = loadMilestones(q);
        List<HrBoardVO.ProgressRow> progress = loadProgress(q);
        HrBoardVO vo = new HrBoardVO();
        vo.setFunnel(buildFunnel(rows, q));
        String cycleJob = blankToNull(q.getCycleJob());
        vo.setCycleJob(cycleJob);
        vo.setJobCycle(buildJobCycle(rows, q, cycleJob));
        vo.setStageCycle(buildStageCycle(rows, q));
        vo.setHc(buildHc(progress));
        vo.setInterviewStats(buildInterviewStats(rows, q));
        vo.setInterviewerPass(buildInterviewerPass(q));
        vo.setFailReasons(buildFailReasons(q));
        vo.setInterviewVolume(buildInterviewVolume(q));
        return vo;
    }

    public List<String> jobNames() {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT r.job_name
                FROM hr_requisition r
                WHERE r.is_active = 1 AND r.job_name IS NOT NULL AND TRIM(r.job_name) <> ''
                """);
        List<Object> args = new ArrayList<>();
        dataScope.apply(sql, args, "r", null);
        sql.append(" ORDER BY r.job_name");
        return jdbc.query(sql.toString(), (rs, row) -> rs.getString(1), args.toArray());
    }

    public HrBoardDrillVO drill(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        String kind = q.getDrillKind() == null ? "STAGE" : q.getDrillKind().trim().toUpperCase(Locale.ROOT);
        HrBoardDrillVO vo = new HrBoardDrillVO();
        vo.setKind(kind);
        switch (kind) {
            case "HC" -> vo.setRequisitions(hcDrill(q));
            case "INTERVIEW" -> vo.setCandidates(interviewDrill(q));
            case "INTERVIEWER", "FAIL_REASON" -> vo.setInterviews(interviewRecordDrill(q, kind));
            case "VOLUME" -> vo.setCandidates(volumeDrill(q));
            default -> vo.setCandidates(funnelDrill(q));
        }
        return vo;
    }

    private List<HrBoardVO.DrillRow> funnelDrill(HrBoardQueryDTO q) {
        int index = funnelIndex(q.getStageCode());
        if (index < 0) {
            return List.of();
        }
        List<HrBoardVO.DrillRow> rows = new ArrayList<>();
        for (Milestone milestone : loadMilestones(q)) {
            if (!inWindow(milestone.submittedAt, q.getStartDate(), q.getEndDate())) {
                continue;
            }
            if (!reached(milestone)[index]) {
                continue;
            }
            rows.add(toDrill(milestone, FUNNEL[index].name(), reachedDate(milestone, index)));
        }
        rows.sort(Comparator.comparing(HrBoardVO.DrillRow::getReachedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HrBoardVO.DrillRow::getApplicationId, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    private List<HrBoardVO.ProgressRow> hcDrill(HrBoardQueryDTO q) {
        String metric = q.getHcMetric() == null ? "DEMAND" : q.getHcMetric().trim().toUpperCase(Locale.ROOT);
        List<HrBoardVO.ProgressRow> rows = new ArrayList<>();
        for (HrBoardVO.ProgressRow row : loadProgress(q)) {
            if (matchHcMetric(row, metric)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static boolean matchHcMetric(HrBoardVO.ProgressRow row, String metric) {
        return switch (metric) {
            case "ARRIVED" -> row.getArrived() > 0;
            case "GAP" -> row.getGap() > 0;
            case "CLOSED" -> isClosed(row.getStatus());
            case "FROZEN" -> "PAUSED".equals(row.getStatus());
            case "RATE", "DEMAND" -> isDemand(row.getStatus());
            default -> true;
        };
    }

    private List<HrBoardVO.DrillRow> interviewDrill(HrBoardQueryDTO q) {
        String metric = q.getInterviewMetric() == null ? "" : q.getInterviewMetric().trim().toUpperCase(Locale.ROOT);
        List<HrBoardVO.DrillRow> rows = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Milestone milestone : loadMilestones(q)) {
            if (!inWindow(milestone.submittedAt, q.getStartDate(), q.getEndDate())) {
                continue;
            }
            boolean[] flags = reached(milestone);
            boolean match = switch (metric) {
                case "PENDING_INVITE" -> flags[0] && !flags[1];
                case "INVITED" -> flags[1];
                case "SHOW_UP" -> flags[2];
                case "ROUND1" -> milestone.firstAt != null || flags[2];
                case "RETEST" -> milestone.secondAt != null || flags[3];
                case "FINAL" -> milestone.finalAt != null || flags[4];
                case "ROUND1_PASS" -> isRoundPass(milestone.applicationId, 1);
                case "RETEST_PASS" -> isRoundPass(milestone.applicationId, 2);
                case "FINAL_PASS" -> flags[4];
                case "NO_SHOW" -> isNoShow(milestone, now);
                default -> true;
            };
            if (match) {
                rows.add(toDrill(milestone, metricLabel(metric), milestone.submittedAt));
            }
        }
        return rows;
    }

    private boolean isRoundPass(long applicationId, int roundNo) {
        Integer hit = jdbc.query("""
                SELECT 1 FROM hr_interview_record
                WHERE application_id = ? AND round_no = ? AND is_active = 1 AND conclusion = 'PASS'
                LIMIT 1
                """, rs -> rs.next() ? 1 : null, applicationId, roundNo);
        return hit != null;
    }

    private static boolean isNoShow(Milestone milestone, LocalDateTime now) {
        return milestone.inviteAt != null
                && milestone.firstAt != null
                && milestone.firstAt.isBefore(now)
                && milestone.showAt == null
                && !reached(milestone)[2];
    }

    private List<HrBoardVO.InterviewRecordRow> interviewRecordDrill(HrBoardQueryDTO q, String kind) {
        StringBuilder sql = new StringBuilder("""
                SELECT rec.id record_id, rec.application_id, c.display_name candidate_name,
                       COALESCE(r.job_name, '') job_name, rec.round_no, rec.conclusion, rec.fail_reason, rec.comment,
                       rec.interviewed_at, rec.interviewer_user_id, u.nickname interviewer_name
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = COALESCE(rec.requisition_id, a.requisition_id) AND r.is_active = 1
                LEFT JOIN sys_user u ON u.user_id = rec.interviewer_user_id
                WHERE rec.is_active = 1 AND rec.interviewed_at IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        if ("FAIL_REASON".equals(kind)) {
            sql.append(" AND rec.conclusion = 'FAIL' ");
            String reason = blankToNull(q.getFailReason());
            if (reason == null || "未填写".equals(reason)) {
                sql.append(" AND (rec.fail_reason IS NULL OR TRIM(rec.fail_reason) = '') ");
            } else {
                sql.append(" AND TRIM(rec.fail_reason) = ? ");
                args.add(reason);
            }
        } else {
            if (q.getInterviewerUserId() == null) {
                return List.of();
            }
            sql.append(" AND rec.conclusion IN ('PASS', 'FAIL') AND rec.interviewer_user_id = ? ");
            args.add(q.getInterviewerUserId());
        }
        appendDateTime(sql, args, "rec.interviewed_at", q);
        appendShared(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        sql.append(" ORDER BY rec.interviewed_at DESC, rec.id DESC LIMIT 500");
        String grain = grainOf(q);
        String axis = blankToNull(q.getAxis());
        List<HrBoardVO.InterviewRecordRow> rows = new ArrayList<>();
        jdbc.query(sql.toString(), rs -> {
            if (rs.getTimestamp("interviewed_at") != null) {
                LocalDateTime at = rs.getTimestamp("interviewed_at").toLocalDateTime();
                if (axis != null && !axis.equals(bucket(at.toLocalDate(), grain))) {
                    return;
                }
            }
            rows.add(mapInterviewRecord(rs));
        }, args.toArray());
        return rows;
    }

    private static HrBoardVO.InterviewRecordRow mapInterviewRecord(ResultSet rs) throws SQLException {
        HrBoardVO.InterviewRecordRow row = new HrBoardVO.InterviewRecordRow();
        row.setRecordId(rs.getLong("record_id"));
        row.setApplicationId(rs.getLong("application_id"));
        row.setCandidateName(rs.getString("candidate_name"));
        row.setJobName(rs.getString("job_name"));
        int roundNo = rs.getInt("round_no");
        row.setRoundNo(roundNo);
        row.setRoundName(roundName(roundNo));
        row.setConclusion(rs.getString("conclusion"));
        row.setFailReason(rs.getString("fail_reason"));
        row.setComment(rs.getString("comment"));
        row.setInterviewedAt(rs.getTimestamp("interviewed_at").toLocalDateTime());
        row.setInterviewerUserId(rs.getLong("interviewer_user_id"));
        row.setInterviewerName(rs.getString("interviewer_name"));
        return row;
    }

    private List<HrBoardVO.ProgressRow> loadProgress(HrBoardQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.job_name, r.headcount, r.status, r.priority, r.target_text, r.received_date, r.onboard_date,
                       r.location_code,
                       (SELECT GROUP_CONCAT(DISTINCT u.nickname SEPARATOR '、')
                          FROM hr_requisition_owner ro
                          LEFT JOIN sys_user u ON u.user_id = ro.user_id
                          WHERE ro.requisition_id = r.id AND ro.is_active = 1) owner_name,
                       (SELECT COUNT(DISTINCT a.id)
                          FROM hr_application a
                          LEFT JOIN hr_onboard o ON o.application_id = a.id AND o.is_active = 1
                          WHERE a.requisition_id = r.id AND a.is_active = 1
                            AND (o.onboard_date IS NOT NULL OR a.current_stage = 'ONBOARDED')) arrived_cnt,
                       (SELECT COUNT(1) FROM hr_application a WHERE a.requisition_id = r.id AND a.is_active = 1) app_cnt,
                       (SELECT COUNT(1) FROM hr_interview_invite i
                          JOIN hr_application a ON a.id = i.application_id AND a.is_active = 1
                          WHERE a.requisition_id = r.id AND i.is_active = 1 AND i.status <> 'CANCELLED') invite_cnt,
                       (SELECT COUNT(1) FROM hr_interview_record rec
                          JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                          WHERE a.requisition_id = r.id AND rec.is_active = 1) record_cnt,
                       (SELECT COUNT(1) FROM hr_application a
                          WHERE a.requisition_id = r.id AND a.is_active = 1
                            AND a.current_stage IN ('SALARY','BG_COLLECT','BG_CHECK','MEDICAL','OFFER_PENDING','PENDING_ONBOARD','OFFER_SENT','OFFER_ACCEPTED','ONBOARDED','FINAL')) offer_cnt
                FROM hr_requisition r
                WHERE r.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        appendRequisitionFilter(sql, args, q, "r");
        if (q.getChannelCode() != null && !q.getChannelCode().isBlank()) {
            sql.append("""
                     AND EXISTS (
                       SELECT 1 FROM hr_application a
                       WHERE a.requisition_id = r.id AND a.is_active = 1 AND a.channel_code = ?
                     )
                    """);
            args.add(q.getChannelCode().trim());
        }
        dataScope.apply(sql, args, "r", null);
        sql.append(" ORDER BY r.received_date DESC, r.id DESC");
        LocalDateTime now = LocalDateTime.now();
        return jdbc.query(sql.toString(), (rs, row) -> mapProgress(rs, now), args.toArray());
    }

    private static HrBoardVO.ProgressRow mapProgress(ResultSet rs, LocalDateTime now) throws SQLException {
        HrBoardVO.ProgressRow row = new HrBoardVO.ProgressRow();
        row.setId(rs.getLong("id"));
        row.setJobName(rs.getString("job_name"));
        int headcount = rs.getInt("headcount");
        row.setHeadcount(headcount);
        String status = rs.getString("status");
        row.setStatus(status);
        int priorityValue = rs.getInt("priority");
        Integer priority = rs.wasNull() ? null : priorityValue;
        row.setPriority(priority);
        row.setPriorityLabel(priorityLabel(priority));
        String targetText = rs.getString("target_text");
        row.setTargetText(targetText);
        LocalDate targetDate = parseTargetDate(targetText);
        row.setTargetDate(targetDate);
        row.setReceivedDate(dateOf(rs, "received_date"));
        row.setOnboardDate(dateOf(rs, "onboard_date"));
        row.setOwnerName(rs.getString("owner_name"));
        row.setLocation("XJ".equals(rs.getString("location_code")) ? "新疆" : "上海");
        int arrived = Math.min(headcount, rs.getInt("arrived_cnt"));
        if (arrived == 0 && rs.getDate("onboard_date") != null) {
            arrived = headcount;
        }
        row.setArrived(arrived);
        int gap = Math.max(0, headcount - arrived);
        if (!"OPEN".equals(status)) {
            gap = "PAUSED".equals(status) ? headcount : 0;
        }
        row.setGap(gap);
        int appCnt = rs.getInt("app_cnt");
        int inviteCnt = rs.getInt("invite_cnt");
        int recordCnt = rs.getInt("record_cnt");
        int offerCnt = rs.getInt("offer_cnt");
        row.setProgressStatus(progressStatus(status, arrived, appCnt, inviteCnt, recordCnt, offerCnt));
        boolean unfinished = !"DONE".equals(status) && !"STOPPED".equals(status) && !"ARCHIVED".equals(status) && arrived < headcount;
        boolean dateWarn = unfinished && targetDate != null && !targetDate.atStartOfDay().isAfter(now.plusHours(48));
        row.setWarning(dateWarn || (unfinished && priority != null && priority == 1 && targetDate == null));
        return row;
    }

    private static String progressStatus(String status, int arrived, int appCnt, int inviteCnt, int recordCnt, int offerCnt) {
        if ("PAUSED".equals(status)) {
            return "暂停冻结";
        }
        if ("DONE".equals(status) || "STOPPED".equals(status) || "ARCHIVED".equals(status) || arrived > 0 && "DONE".equals(status)) {
            return "已完成";
        }
        if (offerCnt > 0) {
            return "offer中";
        }
        if (inviteCnt > 0 || recordCnt > 0) {
            return "面试中";
        }
        if (appCnt > 0) {
            return "简历收集中";
        }
        return "待启动";
    }

    private HrBoardVO.HcBlock buildHc(List<HrBoardVO.ProgressRow> rows) {
        HrBoardVO.HcBlock block = new HrBoardVO.HcBlock();
        block.setRows(rows);
        for (HrBoardVO.ProgressRow row : rows) {
            if (isDemand(row.getStatus())) {
                block.setDemand(block.getDemand() + row.getHeadcount());
                block.setArrived(block.getArrived() + row.getArrived());
                if ("OPEN".equals(row.getStatus())) {
                    block.setGap(block.getGap() + row.getGap());
                }
            }
            if (isClosed(row.getStatus())) {
                block.setClosed(block.getClosed() + 1);
            }
            if ("PAUSED".equals(row.getStatus())) {
                block.setFrozen(block.getFrozen() + row.getHeadcount());
            }
        }
        if (block.getDemand() > 0) {
            block.setCompletionRate(block.getArrived() * 1.0 / block.getDemand());
        }
        return block;
    }

    private HrBoardVO.InterviewStats buildInterviewStats(List<Milestone> rows, HrBoardQueryDTO q) {
        HrBoardVO.InterviewStats stats = new HrBoardVO.InterviewStats();
        LocalDateTime now = LocalDateTime.now();
        long round1Pass = 0;
        long round1Done = 0;
        long retestPass = 0;
        long retestDone = 0;
        long finalPass = 0;
        long finalDone = 0;
        long invitedWithTime = 0;
        for (Milestone milestone : rows) {
            if (!inWindow(milestone.submittedAt, q.getStartDate(), q.getEndDate())) {
                continue;
            }
            boolean[] flags = reached(milestone);
            if (flags[0] && !flags[1]) {
                stats.setPendingInvite(stats.getPendingInvite() + 1);
            }
            if (flags[1]) {
                stats.setInvited(stats.getInvited() + 1);
            }
            if (flags[2]) {
                stats.setShowUp(stats.getShowUp() + 1);
            }
            if (milestone.firstAt != null || flags[2]) {
                stats.setRound1(stats.getRound1() + 1);
            }
            if (milestone.secondAt != null || flags[3]) {
                stats.setRetest(stats.getRetest() + 1);
            }
            if (milestone.finalAt != null || flags[4]) {
                stats.setFinalRound(stats.getFinalRound() + 1);
            }
            if (flags[1] && milestone.firstAt != null && milestone.firstAt.isBefore(now)) {
                invitedWithTime++;
                if (isNoShow(milestone, now)) {
                    stats.setNoShow(stats.getNoShow() + 1);
                }
            }
        }
        Map<Integer, long[]> passBag = loadRoundPassBag(q);
        long[] r1 = passBag.getOrDefault(1, new long[2]);
        long[] r2 = passBag.getOrDefault(2, new long[2]);
        long[] rFinal = finalPassBag(q);
        round1Pass = r1[0];
        round1Done = r1[1];
        retestPass = r2[0];
        retestDone = r2[1];
        finalPass = rFinal[0];
        finalDone = rFinal[1];
        stats.setRound1PassRate(rate(round1Pass, round1Done));
        stats.setRetestPassRate(rate(retestPass, retestDone));
        stats.setFinalPassRate(rate(finalPass, finalDone));
        stats.setNoShowRate(rate(stats.getNoShow(), invitedWithTime));
        return stats;
    }

    private Map<Integer, long[]> loadRoundPassBag(HrBoardQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT rec.round_no,
                       SUM(CASE WHEN rec.conclusion = 'PASS' THEN 1 ELSE 0 END) pass_cnt,
                       SUM(CASE WHEN rec.conclusion IN ('PASS','FAIL') THEN 1 ELSE 0 END) done_cnt
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = COALESCE(rec.requisition_id, a.requisition_id) AND r.is_active = 1
                WHERE rec.is_active = 1 AND rec.conclusion IN ('PASS','FAIL') AND rec.round_no IN (1, 2)
                """);
        List<Object> args = new ArrayList<>();
        appendDateTime(sql, args, "rec.interviewed_at", q);
        appendShared(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        sql.append(" GROUP BY rec.round_no");
        Map<Integer, long[]> map = new HashMap<>();
        jdbc.query(sql.toString(), rs -> {
            map.put(rs.getInt("round_no"), new long[]{rs.getLong("pass_cnt"), rs.getLong("done_cnt")});
        }, args.toArray());
        return map;
    }

    private long[] finalPassBag(HrBoardQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT SUM(CASE WHEN rec.conclusion = 'PASS' THEN 1 ELSE 0 END) pass_cnt,
                       SUM(CASE WHEN rec.conclusion IN ('PASS','FAIL') THEN 1 ELSE 0 END) done_cnt
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = COALESCE(rec.requisition_id, a.requisition_id) AND r.is_active = 1
                WHERE rec.is_active = 1 AND rec.conclusion IN ('PASS','FAIL')
                  AND (
                    EXISTS (
                      SELECT 1 FROM hr_requisition_round x
                      WHERE x.requisition_id = r.id AND x.is_active = 1 AND x.round_no = rec.round_no
                        AND x.round_no = (SELECT MAX(y.round_no) FROM hr_requisition_round y WHERE y.requisition_id = r.id AND y.is_active = 1)
                    )
                    OR (r.id IS NULL AND rec.round_no >= 3)
                  )
                """);
        List<Object> args = new ArrayList<>();
        appendDateTime(sql, args, "rec.interviewed_at", q);
        appendShared(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        long[] result = {0L, 0L};
        jdbc.query(sql.toString(), rs -> {
            result[0] = rs.getLong("pass_cnt");
            result[1] = rs.getLong("done_cnt");
        }, args.toArray());
        return result;
    }

    private List<HrBoardVO.ChartPoint> buildInterviewerPass(HrBoardQueryDTO q) {
        String grain = grainOf(q);
        StringBuilder sql = new StringBuilder("""
                SELECT rec.interviewed_at, rec.interviewer_user_id, COALESCE(u.nickname, CONCAT('用户', rec.interviewer_user_id)) interviewer_name,
                       rec.conclusion
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = COALESCE(rec.requisition_id, a.requisition_id) AND r.is_active = 1
                LEFT JOIN sys_user u ON u.user_id = rec.interviewer_user_id
                WHERE rec.is_active = 1 AND rec.conclusion IN ('PASS','FAIL') AND rec.interviewed_at IS NOT NULL
                  AND rec.interviewer_user_id IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        appendDateTime(sql, args, "rec.interviewed_at", q);
        appendShared(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        Map<String, Map<String, long[]>> bag = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        jdbc.query(sql.toString(), rs -> {
            LocalDateTime at = rs.getTimestamp("interviewed_at").toLocalDateTime();
            String axis = bucket(at.toLocalDate(), grain);
            String key = String.valueOf(rs.getLong("interviewer_user_id"));
            names.put(key, rs.getString("interviewer_name"));
            long[] cell = bag.computeIfAbsent(axis, ignored -> new LinkedHashMap<>()).computeIfAbsent(key, ignored -> new long[2]);
            cell[1]++;
            if ("PASS".equals(rs.getString("conclusion"))) {
                cell[0]++;
            }
        }, args.toArray());
        List<HrBoardVO.ChartPoint> points = new ArrayList<>();
        List<String> axes = new ArrayList<>(bag.keySet());
        axes.sort(Comparator.naturalOrder());
        for (String axis : axes) {
            List<String> keys = new ArrayList<>(bag.get(axis).keySet());
            keys.sort(Comparator.comparing(key -> names.getOrDefault(key, key)));
            for (String key : keys) {
                long[] cell = bag.get(axis).get(key);
                if (cell == null || cell[1] == 0) {
                    continue;
                }
                HrBoardVO.ChartPoint point = new HrBoardVO.ChartPoint();
                point.setAxis(axis);
                point.setSeries(names.getOrDefault(key, key));
                point.setSeriesKey(key);
                point.setValue(Math.round(cell[0] * 1000.0 / cell[1]) / 10.0);
                point.setDrillable(true);
                points.add(point);
            }
        }
        return points;
    }

    private List<HrBoardVO.PieSlice> buildFailReasons(HrBoardQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(NULLIF(TRIM(rec.fail_reason), ''), '未填写') reason_name, COUNT(1) cnt
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = COALESCE(rec.requisition_id, a.requisition_id) AND r.is_active = 1
                WHERE rec.is_active = 1 AND rec.conclusion = 'FAIL'
                """);
        List<Object> args = new ArrayList<>();
        appendDateTime(sql, args, "rec.interviewed_at", q);
        appendShared(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        sql.append(" GROUP BY COALESCE(NULLIF(TRIM(rec.fail_reason), ''), '未填写')");
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(sql.toString(), rs -> {
            counts.put(rs.getString("reason_name"), rs.getLong("cnt"));
        }, args.toArray());
        List<String> ordered = jdbc.query("""
                SELECT name FROM hr_fail_reason WHERE is_active = 1 ORDER BY sort_no, id
                """, (rs, row) -> rs.getString(1));
        List<HrBoardVO.PieSlice> slices = new ArrayList<>();
        for (String name : ordered) {
            long value = counts.getOrDefault(name, 0L);
            if (value <= 0) {
                continue;
            }
            HrBoardVO.PieSlice slice = new HrBoardVO.PieSlice();
            slice.setName(name);
            slice.setKey(name);
            slice.setValue(value);
            slices.add(slice);
            counts.remove(name);
        }
        List<String> extras = new ArrayList<>(counts.keySet());
        extras.sort(Comparator.naturalOrder());
        for (String name : extras) {
            long value = counts.getOrDefault(name, 0L);
            if (value <= 0) {
                continue;
            }
            HrBoardVO.PieSlice slice = new HrBoardVO.PieSlice();
            slice.setName(name);
            slice.setKey(name);
            slice.setValue(value);
            slices.add(slice);
        }
        return slices;
    }

    private List<HrBoardVO.ChartPoint> buildInterviewVolume(HrBoardQueryDTO q) {
        String grain = grainOf(q);
        StringBuilder sql = new StringBuilder("""
                SELECT rec.interviewed_at,
                       COALESCE(r.id, 0) requisition_id,
                       COALESCE(NULLIF(TRIM(r.job_name), ''), '未关联岗位') job_name,
                       a.id application_id
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = COALESCE(rec.requisition_id, a.requisition_id) AND r.is_active = 1
                WHERE rec.is_active = 1 AND rec.interviewed_at IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        appendDateTime(sql, args, "rec.interviewed_at", q);
        appendShared(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        Map<String, Map<String, java.util.Set<Long>>> bag = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        jdbc.query(sql.toString(), rs -> {
            LocalDateTime at = rs.getTimestamp("interviewed_at").toLocalDateTime();
            String axis = bucket(at.toLocalDate(), grain);
            String key = String.valueOf(rs.getLong("requisition_id"));
            names.put(key, rs.getString("job_name"));
            bag.computeIfAbsent(axis, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(key, ignored -> new java.util.HashSet<>())
                    .add(rs.getLong("application_id"));
        }, args.toArray());
        List<HrBoardVO.ChartPoint> points = new ArrayList<>();
        List<String> axes = new ArrayList<>(bag.keySet());
        axes.sort(Comparator.naturalOrder());
        for (String axis : axes) {
            List<String> keys = new ArrayList<>(bag.get(axis).keySet());
            keys.sort(Comparator.comparing(key -> names.getOrDefault(key, key)));
            for (String key : keys) {
                java.util.Set<Long> apps = bag.get(axis).get(key);
                if (apps == null || apps.isEmpty()) {
                    continue;
                }
                HrBoardVO.ChartPoint point = new HrBoardVO.ChartPoint();
                point.setAxis(axis);
                point.setSeries(names.getOrDefault(key, key));
                point.setSeriesKey(key);
                point.setValue(apps.size());
                point.setDrillable(true);
                points.add(point);
            }
        }
        return points;
    }

    private List<HrBoardVO.DrillRow> volumeDrill(HrBoardQueryDTO q) {
        Long requisitionId = q.getRequisitionId();
        if (requisitionId == null) {
            return List.of();
        }
        String grain = grainOf(q);
        String axis = blankToNull(q.getAxis());
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT a.id application_id, a.requisition_id, c.display_name candidate_name,
                       COALESCE(r.job_name, '') job_name, COALESCE(ch.channel_name, a.channel_code, '') channel_name,
                       COALESCE(sd.stage_name, a.current_stage) stage_name, a.submitter_name, a.submitted_at,
                       r.priority, r.status,
                       (SELECT GROUP_CONCAT(DISTINCT u.nickname SEPARATOR '、')
                          FROM hr_requisition_owner ro
                          LEFT JOIN sys_user u ON u.user_id = ro.user_id
                          WHERE ro.requisition_id = r.id AND ro.is_active = 1) owner_name,
                       MIN(rec.interviewed_at) interviewed_at
                FROM hr_interview_record rec
                JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = COALESCE(rec.requisition_id, a.requisition_id) AND r.is_active = 1
                LEFT JOIN hr_channel ch ON ch.channel_code = a.channel_code
                LEFT JOIN hr_stage_def sd ON sd.stage_code = a.current_stage
                WHERE rec.is_active = 1 AND rec.interviewed_at IS NOT NULL
                  AND COALESCE(r.id, 0) = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(requisitionId);
        appendDateTime(sql, args, "rec.interviewed_at", q);
        appendShared(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        sql.append("""
                 GROUP BY a.id, a.requisition_id, c.display_name, r.job_name, ch.channel_name, a.channel_code,
                          sd.stage_name, a.current_stage, a.submitter_name, a.submitted_at, r.priority, r.status
                 ORDER BY interviewed_at DESC, a.id DESC
                 LIMIT 500
                """);
        List<HrBoardVO.DrillRow> rows = new ArrayList<>();
        jdbc.query(sql.toString(), rs -> {
            LocalDateTime at = rs.getTimestamp("interviewed_at") == null ? null : rs.getTimestamp("interviewed_at").toLocalDateTime();
            if (axis != null && (at == null || !axis.equals(bucket(at.toLocalDate(), grain)))) {
                return;
            }
            HrBoardVO.DrillRow row = new HrBoardVO.DrillRow();
            row.setApplicationId(rs.getLong("application_id"));
            long reqId = rs.getLong("requisition_id");
            row.setRequisitionId(rs.wasNull() ? null : reqId);
            row.setCandidateName(rs.getString("candidate_name"));
            row.setJobName(rs.getString("job_name"));
            row.setChannel(rs.getString("channel_name"));
            row.setStageName(rs.getString("stage_name"));
            row.setSubmitter(rs.getString("submitter_name"));
            row.setSubmittedAt(dateOf(rs, "submitted_at"));
            int priorityValue = rs.getInt("priority");
            Integer priority = rs.wasNull() ? null : priorityValue;
            row.setPriorityLabel(priorityLabel(priority));
            row.setOwnerName(rs.getString("owner_name"));
            row.setStatus(rs.getString("status"));
            row.setInterviewAt(at);
            row.setReachedAt(at == null ? null : at.toLocalDate());
            row.setFunnelStage("面试量");
            rows.add(row);
        }, args.toArray());
        return rows;
    }

    private List<Milestone> loadMilestones(HrBoardQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id application_id,
                       c.display_name candidate_name,
                       COALESCE(NULLIF(TRIM(r.job_name), ''), '未关联岗位') job_name,
                       r.priority,
                       r.received_date,
                       COALESCE(ch.channel_name, a.channel_code, '') channel_name,
                       a.submitted_at,
                       a.resume_status,
                       a.screen_result,
                       a.current_stage,
                       COALESCE(sd.stage_name, a.current_stage) stage_name,
                       (SELECT GROUP_CONCAT(DISTINCT u.nickname SEPARATOR '、')
                          FROM hr_requisition_owner ro
                          LEFT JOIN sys_user u ON u.user_id = ro.user_id
                          WHERE ro.requisition_id = r.id AND ro.is_active = 1) owner_name,
                       (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.is_active = 1 AND e.stage_code = 'SCREEN_PASS') screen_event_at,
                       (SELECT MIN(i.create_time) FROM hr_interview_invite i
                          WHERE i.application_id = a.id AND i.is_active = 1 AND i.status <> 'CANCELLED') invite_at,
                       (SELECT MIN(rec.interviewed_at) FROM hr_interview_record rec
                          WHERE rec.application_id = a.id AND rec.is_active = 1 AND rec.round_no = 1
                            AND rec.conclusion IS NOT NULL) show_record_at,
                       (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.is_active = 1
                            AND e.stage_code IN ('SHOW_UP','FIRST_ROUND','FIRST_PENDING','FIRST_FAIL','R1_DISPUTE')) show_event_at,
                       (SELECT MIN(COALESCE(rec.interviewed_at, i.interview_at))
                          FROM hr_interview_invite i
                          LEFT JOIN hr_interview_record rec ON rec.invite_id = i.id AND rec.is_active = 1
                          WHERE i.application_id = a.id AND i.is_active = 1 AND i.round_no = 1 AND i.status <> 'CANCELLED') first_at,
                       (SELECT MIN(rd.interview_at) FROM hr_interview_round rd
                          WHERE rd.application_id = a.id AND rd.is_active = 1 AND rd.round_no = 2 AND rd.interview_at IS NOT NULL) second_at,
                       (SELECT rd.interview_at FROM hr_interview_round rd
                          WHERE rd.application_id = a.id AND rd.is_active = 1 AND rd.interview_at IS NOT NULL
                            AND r.id IS NOT NULL
                            AND rd.round_no = (SELECT MAX(x.round_no) FROM hr_requisition_round x WHERE x.requisition_id = r.id AND x.is_active = 1)
                          LIMIT 1) final_interview_at,
                       (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.is_active = 1 AND e.stage_code = 'SECOND_ROUND') retest_event_at,
                       (SELECT MIN(rec.interviewed_at) FROM hr_interview_record rec
                          WHERE rec.application_id = a.id AND rec.is_active = 1 AND rec.round_no = 2 AND rec.conclusion = 'PASS') retest_record_at,
                       (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.is_active = 1 AND e.stage_code = 'FINAL') final_event_at,
                       (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.is_active = 1 AND e.stage_code = 'SALARY') salary_at,
                       (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.is_active = 1 AND e.stage_code = 'BG_COLLECT') bg_collect_at,
                       (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.is_active = 1 AND e.stage_code = 'BG_CHECK') bg_check_at,
                       (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.is_active = 1 AND e.stage_code = 'MEDICAL') medical_at,
                       (SELECT MIN(e.event_at) FROM hr_stage_event e
                          WHERE e.application_id = a.id AND e.is_active = 1 AND e.stage_code = 'OFFER_PENDING') offer_pending_at,
                       off.offered_at,
                       off.status offer_status,
                       ob.onboard_date
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id AND c.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                LEFT JOIN hr_channel ch ON ch.channel_code = a.channel_code
                LEFT JOIN hr_stage_def sd ON sd.stage_code = a.current_stage
                LEFT JOIN hr_offer off ON off.application_id = a.id AND off.is_active = 1
                LEFT JOIN hr_onboard ob ON ob.application_id = a.id AND ob.is_active = 1
                WHERE a.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        appendShared(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapMilestone(rs), args.toArray());
    }

    private static Milestone mapMilestone(ResultSet rs) throws SQLException {
        int priorityValue = rs.getInt("priority");
        Integer priority = rs.wasNull() ? null : priorityValue;
        LocalDateTime showAt = firstNonNull(timeOf(rs, "show_record_at"), timeOf(rs, "show_event_at"));
        LocalDateTime firstAt = firstNonNull(timeOf(rs, "first_at"), timeOf(rs, "show_record_at"));
        return new Milestone(
                rs.getLong("application_id"),
                rs.getString("candidate_name"),
                rs.getString("job_name"),
                priority,
                dateOf(rs, "received_date"),
                rs.getString("channel_name"),
                dateOf(rs, "submitted_at"),
                rs.getString("resume_status"),
                rs.getString("screen_result"),
                rs.getString("current_stage"),
                rs.getString("stage_name"),
                rs.getString("owner_name"),
                timeOf(rs, "screen_event_at"),
                timeOf(rs, "invite_at"),
                showAt,
                firstAt,
                timeOf(rs, "second_at"),
                firstNonNull(timeOf(rs, "final_interview_at"), timeOf(rs, "final_event_at")),
                firstNonNull(timeOf(rs, "retest_event_at"), timeOf(rs, "retest_record_at")),
                timeOf(rs, "final_event_at"),
                timeOf(rs, "salary_at"),
                timeOf(rs, "bg_collect_at"),
                timeOf(rs, "bg_check_at"),
                timeOf(rs, "medical_at"),
                timeOf(rs, "offer_pending_at"),
                timeOf(rs, "offered_at"),
                rs.getString("offer_status"),
                dateOf(rs, "onboard_date"));
    }

    private List<HrBoardVO.FunnelNode> buildFunnel(List<Milestone> rows, HrBoardQueryDTO q) {
        Period previous = shift(q.getStartDate(), q.getEndDate(), false);
        Period year = shift(q.getStartDate(), q.getEndDate(), true);
        long[] current = countFunnel(rows, q.getStartDate(), q.getEndDate());
        long[] mom = countFunnel(rows, previous.start(), previous.end());
        long[] yoy = countFunnel(rows, year.start(), year.end());
        List<HrBoardVO.FunnelNode> nodes = new ArrayList<>();
        for (int i = 0; i < FUNNEL.length; i++) {
            HrBoardVO.FunnelNode node = new HrBoardVO.FunnelNode();
            node.setStageCode(FUNNEL[i].code());
            node.setStageName(FUNNEL[i].name());
            node.setCount(current[i]);
            node.setConversionLabel(FUNNEL[i].conversionLabel());
            if (i > 0 && current[i - 1] > 0) {
                node.setConversion(current[i] * 1.0 / current[i - 1]);
            }
            node.setMom(rateDelta(current[i], mom[i]));
            node.setYoy(rateDelta(current[i], yoy[i]));
            nodes.add(node);
        }
        return nodes;
    }

    private static long[] countFunnel(List<Milestone> rows, LocalDate start, LocalDate end) {
        long[] counts = new long[FUNNEL.length];
        for (Milestone milestone : rows) {
            if (!inWindow(milestone.submittedAt, start, end)) {
                continue;
            }
            boolean[] flags = reached(milestone);
            for (int i = 0; i < flags.length; i++) {
                if (flags[i]) {
                    counts[i]++;
                }
            }
        }
        return counts;
    }

    private List<HrBoardVO.ChartPoint> buildJobCycle(List<Milestone> rows, HrBoardQueryDTO q, String cycleJob) {
        Map<String, Map<String, double[]>> bag = new LinkedHashMap<>();
        boolean drilled = cycleJob != null;
        for (Milestone milestone : rows) {
            if (milestone.onboardDate == null || !inWindow(milestone.onboardDate, q.getStartDate(), q.getEndDate())) {
                continue;
            }
            if (drilled && !cycleJob.equals(milestone.jobName)) {
                continue;
            }
            String axis = bucket(milestone.onboardDate, grainOf(q));
            if (!drilled) {
                Long full = betweenDays(milestone.receivedDate, milestone.onboardDate);
                if (full != null) {
                    addAvg(bag, axis, milestone.jobName, full);
                }
                continue;
            }
            Long[] spans = stageDays(milestone);
            for (int i = 0; i < CYCLE_STAGE_NAMES.length; i++) {
                if (spans[i] != null) {
                    addAvg(bag, axis, CYCLE_STAGE_NAMES[i], spans[i]);
                }
            }
        }
        return emitCycle(bag, !drilled);
    }

    private List<HrBoardVO.ChartPoint> buildStageCycle(List<Milestone> rows, HrBoardQueryDTO q) {
        Map<String, Map<String, double[]>> bag = new LinkedHashMap<>();
        String grain = grainOf(q);
        for (Milestone milestone : rows) {
            Long[] spans = stageDays(milestone);
            LocalDate[] ends = stageEnds(milestone);
            for (int i = 0; i < CYCLE_STAGE_NAMES.length; i++) {
                if (spans[i] == null || !inWindow(ends[i], q.getStartDate(), q.getEndDate())) {
                    continue;
                }
                addAvg(bag, bucket(ends[i], grain), CYCLE_STAGE_NAMES[i], spans[i]);
            }
        }
        return emitCycle(bag, false);
    }

    private static Long[] stageDays(Milestone milestone) {
        boolean screened = milestone.screenAt != null || isPass(milestone.resumeStatus) || isPass(milestone.screenResult)
                || reached(milestone)[0];
        LocalDate screenStart = milestone.screenAt != null ? milestone.screenAt.toLocalDate() : (screened ? milestone.submittedAt : null);
        LocalDate first = toDate(milestone.firstAt);
        LocalDate second = toDate(milestone.secondAt);
        LocalDate fin = toDate(milestone.finalAt);
        LocalDate offer = toDate(milestone.offerAt);
        return new Long[]{
                betweenDays(milestone.receivedDate, milestone.submittedAt),
                betweenDays(screenStart, first),
                betweenDays(first, second),
                betweenDays(fin, offer),
                betweenDays(offer, milestone.onboardDate)
        };
    }

    private static LocalDate[] stageEnds(Milestone milestone) {
        return new LocalDate[]{
                milestone.submittedAt,
                toDate(milestone.firstAt),
                toDate(milestone.secondAt),
                toDate(milestone.offerAt),
                milestone.onboardDate
        };
    }

    private static void addAvg(Map<String, Map<String, double[]>> bag, String axis, String series, double days) {
        double[] cell = bag.computeIfAbsent(axis, key -> new HashMap<>()).computeIfAbsent(series, key -> new double[2]);
        cell[0] += days;
        cell[1] += 1;
    }

    private static List<HrBoardVO.ChartPoint> emitCycle(Map<String, Map<String, double[]>> bag, boolean drillable) {
        List<HrBoardVO.ChartPoint> points = new ArrayList<>();
        List<String> axes = new ArrayList<>(bag.keySet());
        axes.sort(Comparator.naturalOrder());
        for (String axis : axes) {
            List<String> series = new ArrayList<>(bag.get(axis).keySet());
            series.sort(Comparator.comparingInt(HrBoardService::seriesOrder).thenComparing(Comparator.naturalOrder()));
            for (String name : series) {
                double[] cell = bag.get(axis).get(name);
                if (cell == null || cell[1] == 0) {
                    continue;
                }
                HrBoardVO.ChartPoint point = new HrBoardVO.ChartPoint();
                point.setAxis(axis);
                point.setSeries(name);
                point.setSeriesKey(name);
                point.setValue(Math.round(cell[0] / cell[1] * 10.0) / 10.0);
                point.setDrillable(drillable);
                points.add(point);
            }
        }
        return points;
    }

    private static int seriesOrder(String name) {
        for (int i = 0; i < CYCLE_STAGE_NAMES.length; i++) {
            if (CYCLE_STAGE_NAMES[i].equals(name)) {
                return i;
            }
        }
        return CYCLE_STAGE_NAMES.length;
    }

    private static boolean[] reached(Milestone milestone) {
        String stage = milestone.currentStage == null ? "" : milestone.currentStage;
        boolean onboard = milestone.onboardDate != null || "ONBOARDED".equals(stage);
        boolean accept = onboard || "ACCEPTED".equalsIgnoreCase(milestone.offerStatus) || "OFFER_ACCEPTED".equals(stage);
        boolean offer = accept || milestone.offerAt != null || AT_OR_AFTER_OFFER_SENT.contains(stage);
        boolean offerPending = offer || milestone.offerPendingAt != null || AT_OR_AFTER_OFFER_PENDING.contains(stage);
        boolean medical = offerPending || milestone.medicalAt != null || AT_OR_AFTER_MEDICAL.contains(stage);
        boolean bgCheck = medical || milestone.bgCheckAt != null || AT_OR_AFTER_BG_CHECK.contains(stage);
        boolean bgCollect = bgCheck || milestone.bgCollectAt != null || AT_OR_AFTER_BG_COLLECT.contains(stage);
        boolean salary = bgCollect || milestone.salaryAt != null || AT_OR_AFTER_SALARY.contains(stage);
        boolean fin = salary || milestone.finalEventAt != null || AT_OR_AFTER_FINAL.contains(stage);
        boolean retest = fin || milestone.retestAt != null || RETEST_PASS.contains(stage);
        boolean show = retest || milestone.showAt != null || SHOW_STAGES.contains(stage);
        boolean invite = show || milestone.inviteAt != null || "INVITED".equals(stage);
        boolean screen = invite || milestone.screenAt != null || isPass(milestone.resumeStatus) || isPass(milestone.screenResult)
                || "SCREEN_PASS".equals(stage);
        return new boolean[]{screen, invite, show, retest, fin, salary, bgCollect, bgCheck, medical, offerPending, offer, accept, onboard};
    }

    private static HrBoardVO.DrillRow toDrill(Milestone milestone, String funnelStage, LocalDate reachedAt) {
        HrBoardVO.DrillRow row = new HrBoardVO.DrillRow();
        row.setApplicationId(milestone.applicationId);
        row.setCandidateName(milestone.candidateName);
        row.setJobName(milestone.jobName);
        row.setChannel(milestone.channelName);
        row.setStageName(milestone.stageName);
        row.setSubmittedAt(milestone.submittedAt);
        row.setOwnerName(milestone.ownerName);
        row.setPriorityLabel(priorityLabel(milestone.priority));
        row.setFunnelStage(funnelStage);
        row.setReachedAt(reachedAt);
        return row;
    }

    private static LocalDate reachedDate(Milestone milestone, int index) {
        return switch (index) {
            case 0 -> milestone.screenAt != null ? milestone.screenAt.toLocalDate() : milestone.submittedAt;
            case 1 -> toDate(milestone.inviteAt);
            case 2 -> toDate(firstNonNull(milestone.showAt, milestone.firstAt));
            case 3 -> toDate(firstNonNull(milestone.retestAt, milestone.secondAt));
            case 4 -> toDate(firstNonNull(milestone.finalAt, milestone.finalEventAt));
            case 5 -> toDate(milestone.salaryAt);
            case 6 -> toDate(milestone.bgCollectAt);
            case 7 -> toDate(milestone.bgCheckAt);
            case 8 -> toDate(milestone.medicalAt);
            case 9 -> toDate(milestone.offerPendingAt);
            case 10 -> toDate(milestone.offerAt);
            case 11 -> milestone.onboardDate != null ? milestone.onboardDate : toDate(milestone.offerAt);
            case 12 -> milestone.onboardDate;
            default -> milestone.submittedAt;
        };
    }

    private void appendRequisitionFilter(StringBuilder sql, List<Object> args, HrBoardQueryDTO q, String requisition) {
        if (q.getStartDate() != null) {
            sql.append(" AND ").append(requisition).append(".received_date >= ? ");
            args.add(q.getStartDate());
        }
        if (q.getEndDate() != null) {
            sql.append(" AND ").append(requisition).append(".received_date <= ? ");
            args.add(q.getEndDate());
        }
        appendShared(sql, args, q, null, requisition);
    }

    private void appendShared(StringBuilder sql, List<Object> args, HrBoardQueryDTO q, String application, String requisition) {
        if (q.getLocationCode() != null && !q.getLocationCode().isBlank()) {
            sql.append(" AND ").append(requisition).append(".location_code = ? ");
            args.add(q.getLocationCode());
        }
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            sql.append(" AND ").append(requisition).append(".status = ? ");
            args.add(q.getStatus());
        }
        if (q.getPriority() != null) {
            sql.append(" AND ").append(requisition).append(".priority = ? ");
            args.add(q.getPriority());
        }
        if (q.getRequisitionId() != null) {
            sql.append(" AND ").append(requisition).append(".id = ? ");
            args.add(q.getRequisitionId());
        }
        if (q.getChannelCode() != null && !q.getChannelCode().isBlank() && application != null) {
            sql.append(" AND ").append(application).append(".channel_code = ? ");
            args.add(q.getChannelCode());
        }
        if (q.getOwnerUserId() != null) {
            sql.append(" AND ").append(requisition).append(".id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(q.getOwnerUserId());
        }
        if (q.getDeptId() != null) {
            sql.append(" AND ").append(requisition).append(".dept_id IN (SELECT id FROM hr_department WHERE is_active = 1 AND (id = ? OR CONCAT(',', ancestors, ',') LIKE CONCAT('%,', ?, ',%'))) ");
            args.add(q.getDeptId());
            args.add(String.valueOf(q.getDeptId()));
        }
        if (q.getJobName() != null && !q.getJobName().isBlank()) {
            sql.append(" AND ").append(requisition).append(".job_name LIKE ? ");
            args.add("%" + q.getJobName().trim() + "%");
        }
        if (q.getJobCategory() != null && !q.getJobCategory().isBlank()) {
            sql.append(" AND ").append(requisition).append(".job_name = ? ");
            args.add(q.getJobCategory().trim());
        }
        if (q.getTargetText() != null && !q.getTargetText().isBlank()) {
            sql.append(" AND TRIM(").append(requisition).append(".target_text) = ? ");
            args.add(q.getTargetText().trim());
        }
    }

    private static void appendDateTime(StringBuilder sql, List<Object> args, String column, HrBoardQueryDTO q) {
        if (q.getStartDate() != null) {
            sql.append(" AND ").append(column).append(" >= ? ");
            args.add(q.getStartDate().atStartOfDay());
        }
        if (q.getEndDate() != null) {
            sql.append(" AND ").append(column).append(" < ? ");
            args.add(q.getEndDate().plusDays(1).atStartOfDay());
        }
    }

    private static int funnelIndex(String code) {
        if (code == null) {
            return -1;
        }
        for (int i = 0; i < FUNNEL.length; i++) {
            if (FUNNEL[i].code().equals(code)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean inWindow(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null) {
            return false;
        }
        if (start != null && date.isBefore(start)) {
            return false;
        }
        return end == null || !date.isAfter(end);
    }

    private static Long betweenDays(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(start, end);
        return days < 0 ? null : days;
    }

    private static LocalDate toDate(LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDate dateOf(ResultSet rs, String column) throws SQLException {
        return rs.getDate(column) == null ? null : rs.getDate(column).toLocalDate();
    }

    private static LocalDateTime timeOf(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    private static LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private static boolean isPass(String value) {
        return "PASS".equalsIgnoreCase(value) || "通过".equals(value);
    }

    private static boolean isDemand(String status) {
        return "OPEN".equals(status) || "DONE".equals(status) || "STOPPED".equals(status);
    }

    private static boolean isClosed(String status) {
        return "DONE".equals(status) || "STOPPED".equals(status) || "ARCHIVED".equals(status);
    }

    private static String priorityLabel(Integer priority) {
        if (priority == null) {
            return "";
        }
        return switch (priority) {
            case 1 -> "紧急";
            case 2 -> "优先";
            case 3 -> "常规";
            default -> String.valueOf(priority);
        };
    }

    private static String roundName(int roundNo) {
        return switch (roundNo) {
            case 1 -> "一面";
            case 2 -> "复试";
            default -> roundNo + "面";
        };
    }

    private static String metricLabel(String metric) {
        return switch (metric) {
            case "PENDING_INVITE" -> "待邀约";
            case "INVITED" -> "已邀约";
            case "SHOW_UP" -> "到面";
            case "ROUND1" -> "一面参与";
            case "RETEST" -> "复试参与";
            case "FINAL" -> "终面参与";
            case "ROUND1_PASS" -> "一面通过";
            case "RETEST_PASS" -> "复试通过";
            case "FINAL_PASS" -> "终面通过";
            case "NO_SHOW" -> "面试爽约";
            default -> metric;
        };
    }

    private static LocalDate parseTargetDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            try {
                return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
            } catch (Exception ignored) {
                // fall through
            }
        }
        Matcher cn = CN_DATE.matcher(text);
        if (cn.find()) {
            try {
                int year = cn.group(1) == null ? LocalDate.now().getYear() : Integer.parseInt(cn.group(1));
                return LocalDate.of(year, Integer.parseInt(cn.group(2)), Integer.parseInt(cn.group(3)));
            } catch (Exception ignored) {
                // fall through
            }
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("yyyy/M/d"))) {
            try {
                return LocalDate.parse(text.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // continue
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static Double rate(long current, long base) {
        if (base <= 0) {
            return null;
        }
        return current * 1.0 / base;
    }

    private static Double rateDelta(long current, long base) {
        if (base <= 0) {
            return null;
        }
        return (current - base) * 1.0 / base;
    }

    private static Period shift(LocalDate start, LocalDate end, boolean year) {
        if (start == null || end == null) {
            return new Period(null, null);
        }
        if (year) {
            return new Period(start.minusYears(1), end.minusYears(1));
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        return new Period(start.minusDays(days), start.minusDays(1));
    }

    private static String grainOf(HrBoardQueryDTO q) {
        String grain = q.getGrain() == null ? "week" : q.getGrain().trim();
        return switch (grain) {
            case "day", "week", "month", "year" -> grain;
            default -> "week";
        };
    }

    private static String bucket(LocalDate date, String grain) {
        return switch (grain) {
            case "month" -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            case "year" -> String.valueOf(date.getYear());
            case "week" -> {
                WeekFields fields = WeekFields.of(Locale.CHINA);
                int week = date.get(fields.weekOfWeekBasedYear());
                int year = date.get(fields.weekBasedYear());
                yield year + "-W" + String.format("%02d", week);
            }
            default -> date.toString();
        };
    }

    private record FunnelStage(String code, String name, String conversionLabel) {
    }

    private record Period(LocalDate start, LocalDate end) {
    }

    private record Milestone(
            long applicationId,
            String candidateName,
            String jobName,
            Integer priority,
            LocalDate receivedDate,
            String channelName,
            LocalDate submittedAt,
            String resumeStatus,
            String screenResult,
            String currentStage,
            String stageName,
            String ownerName,
            LocalDateTime screenAt,
            LocalDateTime inviteAt,
            LocalDateTime showAt,
            LocalDateTime firstAt,
            LocalDateTime secondAt,
            LocalDateTime finalAt,
            LocalDateTime retestAt,
            LocalDateTime finalEventAt,
            LocalDateTime salaryAt,
            LocalDateTime bgCollectAt,
            LocalDateTime bgCheckAt,
            LocalDateTime medicalAt,
            LocalDateTime offerPendingAt,
            LocalDateTime offerAt,
            String offerStatus,
            LocalDate onboardDate) {
    }
}
