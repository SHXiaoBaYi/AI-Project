package com.base.admin.service;

import com.base.admin.domain.dto.HrKpiQueryDTO;
import com.base.admin.domain.vo.HrKpiBoardVO;
import com.base.admin.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招聘 KPI：岗位分级 + 负责人绩效 + 周期/转化可视化。
 */
@Service
@RequiredArgsConstructor
public class HrKpiService {

    private static final Pattern ISO_DATE = Pattern.compile("(20\\d{2})[-/](\\d{1,2})[-/](\\d{1,2})");
    private static final Pattern CN_DATE = Pattern.compile("(?:(20\\d{2})年)?(\\d{1,2})月(\\d{1,2})日?");
    private static final WeekFields WEEK = WeekFields.ISO;

    private final JdbcTemplate jdbc;
    private final HrDataScope dataScope;

    public HrKpiBoardVO board(HrKpiQueryDTO query) {
        HrKpiQueryDTO q = normalize(query);
        List<JobSnap> jobs = loadJobs(q);
        List<OnboardSnap> onboardings = loadOnboardings(q);

        HrKpiBoardVO vo = new HrKpiBoardVO();
        HrKpiBoardVO.Summary summary = vo.getSummary();
        summary.setOpenJobs((int) jobs.stream().filter(j -> "OPEN".equals(j.status)).count());
        summary.setOnboardedCount(onboardings.size());

        List<HrKpiBoardVO.JobRow> overdue = new ArrayList<>();
        int overdueCompleted = 0;
        int overQuota = 0;
        int completed = 0;
        int onTime = 0;
        double cycleSum = 0;
        int cycleCnt = 0;

        for (JobSnap job : jobs) {
            if (job.overdueDays != null && job.overdueDays > 0 && "OPEN".equals(job.status) && job.arrived < job.headcount) {
                overdue.add(toJobRow(job));
            }
            boolean finished = job.arrived >= job.headcount || "DONE".equals(job.status) || "ARCHIVED".equals(job.status);
            if (finished) {
                completed++;
                LocalDate doneDay = job.onboardDate != null ? job.onboardDate : job.receivedDate;
                if (job.targetDate != null && doneDay != null && doneDay.isAfter(job.targetDate)) {
                    overdueCompleted++;
                } else if (job.targetDate == null || doneDay == null || !doneDay.isAfter(job.targetDate)) {
                    onTime++;
                }
            }
            if (job.arrived > job.headcount) {
                overQuota++;
            }
            if (job.receivedDate != null && job.onboardDate != null) {
                cycleSum += ChronoUnit.DAYS.between(job.receivedDate, job.onboardDate);
                cycleCnt++;
            }
        }

        summary.setOverdueCount(overdue.size());
        summary.setOverdueCompletedCount(overdueCompleted);
        summary.setOverQuotaCount(overQuota);
        summary.setOnTimeRate(completed == 0 ? null : onTime * 1.0 / completed);
        summary.setAvgCycleDays(cycleCnt == 0 ? null : round1(cycleSum / cycleCnt));

        int screened = countScreened(q);
        summary.setConversionRate(screened == 0 ? null : onboardings.size() * 1.0 / screened);

        Map<Long, String> nicknameCache = loadNicknames(collectOwnerIds(jobs, onboardings));
        vo.setOverdueJobs(overdue.stream()
                .sorted(Comparator.comparing((HrKpiBoardVO.JobRow r) -> r.getOverdueDays() == null ? 0 : r.getOverdueDays()).reversed())
                .map(row -> sanitizeJobOwners(row, jobs, q.getOwnerUserId(), nicknameCache))
                .toList());
        vo.setOnboardings(onboardings.stream()
                .map(o -> sanitizeOnboardOwners(toOnboardRow(o), o, q.getOwnerUserId(), nicknameCache))
                .toList());
        vo.setOwners(buildOwners(jobs, onboardings, q, nicknameCache));
        vo.setOnboardTrend(buildOnboardTrend(onboardings, q));
        vo.setConversionFunnel(buildConversionFunnel(q));
        vo.setStageCycle(buildStageCycle(q));
        vo.setOwnerRadar(buildRadar(vo.getOwners()));
        vo.setGradeDistribution(buildGradeDistribution(jobs));
        return vo;
    }

    public void export(HrKpiQueryDTO query, HttpServletResponse response) {
        HrKpiBoardVO board = board(query);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet summary = wb.createSheet("汇总");
            writeRow(summary, 0, "指标", "值");
            writeRow(summary, 1, "在招岗位", String.valueOf(board.getSummary().getOpenJobs()));
            writeRow(summary, 2, "成功入职", String.valueOf(board.getSummary().getOnboardedCount()));
            writeRow(summary, 3, "按期完成率", pct(board.getSummary().getOnTimeRate()));
            writeRow(summary, 4, "逾期岗位", String.valueOf(board.getSummary().getOverdueCount()));
            writeRow(summary, 5, "超期完成", String.valueOf(board.getSummary().getOverdueCompletedCount()));
            writeRow(summary, 6, "超额完成", String.valueOf(board.getSummary().getOverQuotaCount()));
            writeRow(summary, 7, "筛选→入职转化率", pct(board.getSummary().getConversionRate()));
            writeRow(summary, 8, "平均周期(天)", String.valueOf(board.getSummary().getAvgCycleDays()));

            Sheet owners = wb.createSheet("负责人绩效");
            writeRow(owners, 0, "负责人", "在招", "入职", "按期率", "逾期岗", "超期完成", "超额", "转化率", "平均周期");
            int r = 1;
            for (HrKpiBoardVO.OwnerRow o : board.getOwners()) {
                writeRow(owners, r++,
                        o.getOwnerName(),
                        String.valueOf(o.getOpenJobs()),
                        String.valueOf(o.getOnboardedCount()),
                        pct(o.getOnTimeRate()),
                        String.valueOf(o.getOverdueCount()),
                        String.valueOf(o.getOverdueCompletedCount()),
                        String.valueOf(o.getOverQuotaCount()),
                        pct(o.getConversionRate()),
                        String.valueOf(o.getAvgCycleDays()));
            }

            Sheet overdue = wb.createSheet("逾期岗位");
            writeRow(overdue, 0, "岗位", "部门", "负责人", "目标日", "逾期天", "HC", "已入职");
            r = 1;
            for (HrKpiBoardVO.JobRow j : board.getOverdueJobs()) {
                writeRow(overdue, r++,
                        j.getJobName(), j.getDeptName(), j.getOwnerNames(),
                        j.getTargetDate() == null ? "" : j.getTargetDate().toString(),
                        String.valueOf(j.getOverdueDays()),
                        String.valueOf(j.getHeadcount()),
                        String.valueOf(j.getArrived()));
            }

            Sheet onboard = wb.createSheet("入职明细");
            writeRow(onboard, 0, "候选人", "岗位", "负责人", "入职日", "周期天");
            r = 1;
            for (HrKpiBoardVO.OnboardRow o : board.getOnboardings()) {
                writeRow(onboard, r++,
                        o.getCandidateName(), o.getJobName(), o.getOwnerNames(),
                        o.getOnboardDate() == null ? "" : o.getOnboardDate().toString(),
                        String.valueOf(o.getCycleDays()));
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String filename = URLEncoder.encode("招聘KPI报表.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
            wb.write(response.getOutputStream());
        } catch (Exception e) {
            throw new BusinessException("导出失败：" + e.getMessage());
        }
    }

    private HrKpiQueryDTO normalize(HrKpiQueryDTO raw) {
        HrKpiQueryDTO q = raw == null ? new HrKpiQueryDTO() : raw;
        LocalDate end = q.getEndDate() == null ? LocalDate.now() : q.getEndDate();
        LocalDate start = q.getStartDate() == null ? end.minusMonths(1) : q.getStartDate();
        if (end.isBefore(start)) {
            throw new BusinessException("结束日期不能早于开始日期");
        }
        q.setStartDate(start);
        q.setEndDate(end);
        String grain = q.getGrain() == null ? "month" : q.getGrain().trim().toLowerCase(Locale.ROOT);
        if (!List.of("day", "week", "month", "quarter").contains(grain)) {
            grain = "month";
        }
        q.setGrain(grain);
        return q;
    }

    private List<JobSnap> loadJobs(HrKpiQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.job_name, r.status, r.priority, r.importance_level, r.urgency_level, r.difficulty_level,
                       r.headcount, r.target_text, r.received_date, r.onboard_date, r.location_code, d.name dept_name,
                       (SELECT GROUP_CONCAT(COALESCE(u.nickname, o.alias) ORDER BY o.sort_no SEPARATOR '、')
                        FROM hr_requisition_owner o LEFT JOIN sys_user u ON u.user_id = o.user_id
                        WHERE o.requisition_id = r.id AND o.is_active = 1) owner_names,
                       (SELECT GROUP_CONCAT(o.user_id ORDER BY o.sort_no)
                        FROM hr_requisition_owner o
                        WHERE o.requisition_id = r.id AND o.is_active = 1 AND o.user_id IS NOT NULL) owner_ids,
                       (SELECT COUNT(1) FROM hr_application a
                        LEFT JOIN hr_onboard ob ON ob.application_id = a.id AND ob.is_active = 1
                        WHERE a.requisition_id = r.id AND a.is_active = 1
                          AND (a.current_stage = 'ONBOARDED' OR ob.onboard_date IS NOT NULL)) arrived_cnt
                FROM hr_requisition r
                LEFT JOIN hr_department d ON d.id = r.dept_id
                WHERE r.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (q.getOwnerUserId() != null) {
            sql.append(" AND r.id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(q.getOwnerUserId());
        }
        if (q.getImportanceLevel() != null) {
            sql.append(" AND r.importance_level = ? ");
            args.add(q.getImportanceLevel());
        }
        if (q.getPriority() != null) {
            sql.append(" AND r.priority = ? ");
            args.add(q.getPriority());
        }
        if (q.getDifficultyLevel() != null) {
            sql.append(" AND r.difficulty_level = ? ");
            args.add(q.getDifficultyLevel());
        }
        dataScope.apply(sql, args, "r", null);
        LocalDate today = LocalDate.now();
        return jdbc.query(sql.toString(), (rs, i) -> {
            JobSnap j = new JobSnap();
            j.id = rs.getLong("id");
            j.jobName = rs.getString("job_name");
            j.deptName = rs.getString("dept_name");
            j.status = rs.getString("status");
            int p = rs.getInt("priority");
            j.priority = rs.wasNull() ? null : p;
            int imp = rs.getInt("importance_level");
            j.importance = rs.wasNull() ? null : imp;
            j.urgency = j.priority; // 紧急程度即优先级
            int dif = rs.getInt("difficulty_level");
            j.difficulty = rs.wasNull() ? null : dif;
            j.headcount = Math.max(1, rs.getInt("headcount"));
            j.arrived = rs.getInt("arrived_cnt");
            j.targetText = rs.getString("target_text");
            j.targetDate = parseTargetDate(j.targetText);
            Date received = rs.getDate("received_date");
            j.receivedDate = received == null ? null : received.toLocalDate();
            Date onboard = rs.getDate("onboard_date");
            j.onboardDate = onboard == null ? null : onboard.toLocalDate();
            if (j.targetDate == null && j.receivedDate != null) {
                j.targetDate = j.receivedDate.plusDays(30);
            }
            j.ownerNames = rs.getString("owner_names");
            j.ownerIds = parseIds(rs.getString("owner_ids"));
            if ("OPEN".equals(j.status) && j.targetDate != null && j.arrived < j.headcount && j.targetDate.isBefore(today)) {
                j.overdueDays = (int) ChronoUnit.DAYS.between(j.targetDate, today);
            }
            return j;
        }, args.toArray());
    }

    private List<OnboardSnap> loadOnboardings(HrKpiQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id application_id, c.display_name, r.job_name, r.received_date,
                       COALESCE(ob.onboard_date, DATE(e.event_at)) onboard_day,
                       (SELECT GROUP_CONCAT(COALESCE(u.nickname, o.alias) ORDER BY o.sort_no SEPARATOR '、')
                        FROM hr_requisition_owner o LEFT JOIN sys_user u ON u.user_id = o.user_id
                        WHERE o.requisition_id = r.id AND o.is_active = 1) owner_names,
                       (SELECT GROUP_CONCAT(o.user_id ORDER BY o.sort_no)
                        FROM hr_requisition_owner o
                        WHERE o.requisition_id = r.id AND o.is_active = 1 AND o.user_id IS NOT NULL) owner_ids
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                LEFT JOIN hr_onboard ob ON ob.application_id = a.id AND ob.is_active = 1
                LEFT JOIN hr_stage_event e ON e.application_id = a.id AND e.stage_code = 'ONBOARDED' AND e.is_active = 1
                WHERE a.is_active = 1
                  AND (a.current_stage = 'ONBOARDED' OR ob.onboard_date IS NOT NULL)
                  AND COALESCE(ob.onboard_date, DATE(e.event_at)) BETWEEN ? AND ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(q.getStartDate()));
        args.add(Date.valueOf(q.getEndDate()));
        if (q.getOwnerUserId() != null) {
            sql.append(" AND r.id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(q.getOwnerUserId());
        }
        dataScope.apply(sql, args, "r", null);
        return jdbc.query(sql.toString(), (rs, i) -> {
            OnboardSnap o = new OnboardSnap();
            o.applicationId = rs.getLong("application_id");
            o.candidateName = rs.getString("display_name");
            o.jobName = rs.getString("job_name");
            o.ownerNames = rs.getString("owner_names");
            o.ownerIds = parseIds(rs.getString("owner_ids"));
            Date day = rs.getDate("onboard_day");
            o.onboardDate = day == null ? null : day.toLocalDate();
            Date received = rs.getDate("received_date");
            LocalDate receivedDate = received == null ? null : received.toLocalDate();
            if (receivedDate != null && o.onboardDate != null) {
                o.cycleDays = (int) ChronoUnit.DAYS.between(receivedDate, o.onboardDate);
            }
            return o;
        }, args.toArray());
    }

    private int countScreened(HrKpiQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT a.id)
                FROM hr_application a
                JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                WHERE a.is_active = 1
                  AND (a.screen_result = 'PASS' OR a.current_stage IN ('SCREEN_PASS','PHONE_PASS','PHONE_FAIL','INVITED','SHOW_UP','FIRST_ROUND','SECOND_ROUND','FINAL','SALARY','PENDING_ONBOARD','ONBOARDED')
                       OR a.current_stage LIKE 'R%')
                  AND a.submitted_at BETWEEN ? AND ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(q.getStartDate()));
        args.add(Date.valueOf(q.getEndDate()));
        if (q.getOwnerUserId() != null) {
            sql.append(" AND r.id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(q.getOwnerUserId());
        }
        dataScope.apply(sql, args, "r", null);
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    private List<HrKpiBoardVO.OwnerRow> buildOwners(List<JobSnap> jobs, List<OnboardSnap> onboardings,
                                                    HrKpiQueryDTO q, Map<Long, String> nicknameCache) {
        Long filterOwnerId = q.getOwnerUserId();
        Map<Long, HrKpiBoardVO.OwnerRow> map = new LinkedHashMap<>();
        for (JobSnap job : jobs) {
            for (Long id : job.ownerIds) {
                if (filterOwnerId != null && !filterOwnerId.equals(id)) {
                    continue;
                }
                HrKpiBoardVO.OwnerRow row = map.computeIfAbsent(id, this::emptyOwner);
                row.setOwnerName(resolveOwnerName(id, job.ownerNames, job.ownerIds, nicknameCache));
                if ("OPEN".equals(job.status)) {
                    row.setOpenJobs(row.getOpenJobs() + 1);
                }
                if (job.overdueDays != null && job.overdueDays > 0 && "OPEN".equals(job.status)) {
                    row.setOverdueCount(row.getOverdueCount() + 1);
                }
                if (job.arrived > job.headcount) {
                    row.setOverQuotaCount(row.getOverQuotaCount() + 1);
                }
            }
        }
        Map<Long, Integer> onboardCnt = new HashMap<>();
        Map<Long, Integer> cycleSum = new HashMap<>();
        Map<Long, Integer> cycleCnt = new HashMap<>();
        for (OnboardSnap o : onboardings) {
            for (Long id : o.ownerIds) {
                if (filterOwnerId != null && !filterOwnerId.equals(id)) {
                    continue;
                }
                onboardCnt.merge(id, 1, Integer::sum);
                if (o.cycleDays != null) {
                    cycleSum.merge(id, o.cycleDays, Integer::sum);
                    cycleCnt.merge(id, 1, Integer::sum);
                }
                HrKpiBoardVO.OwnerRow row = map.computeIfAbsent(id, this::emptyOwner);
                if (!StringUtils.hasText(row.getOwnerName())) {
                    row.setOwnerName(resolveOwnerName(id, o.ownerNames, o.ownerIds, nicknameCache));
                }
            }
        }
        for (Map.Entry<Long, HrKpiBoardVO.OwnerRow> e : map.entrySet()) {
            Long id = e.getKey();
            HrKpiBoardVO.OwnerRow row = e.getValue();
            row.setOwnerUserId(id);
            row.setOnboardedCount(onboardCnt.getOrDefault(id, 0));
            int cs = cycleSum.getOrDefault(id, 0);
            int cc = cycleCnt.getOrDefault(id, 0);
            row.setAvgCycleDays(cc == 0 ? null : round1(cs * 1.0 / cc));
            int screened = countScreenedForOwner(q, id);
            row.setConversionRate(screened == 0 ? null : row.getOnboardedCount() * 1.0 / screened);
            int finished = 0;
            int onTime = 0;
            int overdueDone = 0;
            for (JobSnap job : jobs) {
                if (!job.ownerIds.contains(id)) {
                    continue;
                }
                boolean done = job.arrived >= job.headcount || "DONE".equals(job.status) || "ARCHIVED".equals(job.status);
                if (!done) {
                    continue;
                }
                finished++;
                LocalDate doneDay = job.onboardDate != null ? job.onboardDate : job.receivedDate;
                if (job.targetDate != null && doneDay != null && doneDay.isAfter(job.targetDate)) {
                    overdueDone++;
                } else {
                    onTime++;
                }
            }
            row.setOverdueCompletedCount(overdueDone);
            row.setOnTimeRate(finished == 0 ? null : onTime * 1.0 / finished);
        }
        return new ArrayList<>(map.values()).stream()
                .sorted(Comparator.comparing((HrKpiBoardVO.OwnerRow o) -> o.getOnboardedCount() == null ? 0 : o.getOnboardedCount()).reversed())
                .toList();
    }

    private int countScreenedForOwner(HrKpiQueryDTO base, Long ownerId) {
        HrKpiQueryDTO q = new HrKpiQueryDTO();
        q.setStartDate(base.getStartDate());
        q.setEndDate(base.getEndDate());
        q.setOwnerUserId(ownerId);
        q.setGrain(base.getGrain());
        return countScreened(q);
    }

    private HrKpiBoardVO.OwnerRow emptyOwner(Long id) {
        HrKpiBoardVO.OwnerRow row = new HrKpiBoardVO.OwnerRow();
        row.setOwnerUserId(id);
        row.setOpenJobs(0);
        row.setOnboardedCount(0);
        row.setOverdueCount(0);
        row.setOverdueCompletedCount(0);
        row.setOverQuotaCount(0);
        return row;
    }

    private List<HrKpiBoardVO.ChartPoint> buildOnboardTrend(List<OnboardSnap> onboardings, HrKpiQueryDTO q) {
        Map<String, Integer> buckets = new LinkedHashMap<>();
        for (OnboardSnap o : onboardings) {
            if (o.onboardDate == null) {
                continue;
            }
            String axis = bucket(o.onboardDate, q.getGrain());
            buckets.merge(axis, 1, Integer::sum);
        }
        List<HrKpiBoardVO.ChartPoint> points = new ArrayList<>();
        for (Map.Entry<String, Integer> e : buckets.entrySet()) {
            HrKpiBoardVO.ChartPoint p = new HrKpiBoardVO.ChartPoint();
            p.setAxis(e.getKey());
            p.setSeries("入职人数");
            p.setValue(e.getValue().doubleValue());
            points.add(p);
        }
        return points;
    }

    /**
     * 漏斗：初筛 → 电话沟通 → 面试 → 待入职（区间内投递且到达该层的人数，含后续层）。
     */
    private List<HrKpiBoardVO.ChartPoint> buildConversionFunnel(HrKpiQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                  COUNT(1) screen_cnt,
                  SUM(CASE WHEN
                        a.screen_result = 'PASS'
                        OR a.current_stage IN ('SCREEN_PASS','PHONE_FAIL','PHONE_PASS','INVITED','SHOW_UP',
                            'FIRST_ROUND','SECOND_ROUND','FINAL','SALARY','BG_COLLECT','BG_CHECK','MEDICAL',
                            'OFFER_PENDING','PENDING_ONBOARD','OFFER_SENT','OFFER_ACCEPTED','ONBOARDED')
                        OR a.current_stage LIKE 'R%'
                        OR EXISTS (SELECT 1 FROM hr_stage_event e WHERE e.application_id = a.id AND e.is_active = 1
                                   AND e.stage_code IN ('SCREEN_PASS','PHONE_PASS','PHONE_FAIL'))
                      THEN 1 ELSE 0 END) phone_cnt,
                  SUM(CASE WHEN
                        ps.result = 'PASS'
                        OR a.current_stage IN ('PHONE_PASS','INVITED','SHOW_UP','FIRST_ROUND','SECOND_ROUND','FINAL',
                            'SALARY','BG_COLLECT','BG_CHECK','MEDICAL','OFFER_PENDING','PENDING_ONBOARD',
                            'OFFER_SENT','OFFER_ACCEPTED','ONBOARDED')
                        OR a.current_stage LIKE 'R%'
                        OR EXISTS (SELECT 1 FROM hr_stage_event e WHERE e.application_id = a.id AND e.is_active = 1
                                   AND e.stage_code IN ('PHONE_PASS','INVITED','SHOW_UP','FIRST_ROUND','FINAL','PENDING_ONBOARD','ONBOARDED'))
                      THEN 1 ELSE 0 END) interview_cnt,
                  SUM(CASE WHEN
                        a.current_stage IN ('SALARY','BG_COLLECT','BG_CHECK','MEDICAL','OFFER_PENDING','PENDING_ONBOARD',
                            'OFFER_SENT','OFFER_ACCEPTED','ONBOARDED','FINAL')
                        OR EXISTS (SELECT 1 FROM hr_stage_event e WHERE e.application_id = a.id AND e.is_active = 1
                                   AND e.stage_code IN ('PENDING_ONBOARD','ONBOARDED','OFFER_PENDING','SALARY'))
                        OR EXISTS (SELECT 1 FROM hr_onboard ob WHERE ob.application_id = a.id AND ob.is_active = 1)
                      THEN 1 ELSE 0 END) onboard_cnt
                FROM hr_application a
                JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                LEFT JOIN hr_phone_screen ps ON ps.application_id = a.id AND ps.is_active = 1
                WHERE a.is_active = 1 AND a.submitted_at BETWEEN ? AND ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(q.getStartDate()));
        args.add(Date.valueOf(q.getEndDate()));
        if (q.getOwnerUserId() != null) {
            sql.append(" AND r.id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(q.getOwnerUserId());
        }
        if (q.getImportanceLevel() != null) {
            sql.append(" AND r.importance_level = ? ");
            args.add(q.getImportanceLevel());
        }
        if (q.getPriority() != null) {
            sql.append(" AND r.priority = ? ");
            args.add(q.getPriority());
        }
        if (q.getDifficultyLevel() != null) {
            sql.append(" AND r.difficulty_level = ? ");
            args.add(q.getDifficultyLevel());
        }
        dataScope.apply(sql, args, "r", null);
        Map<String, Object> row = jdbc.queryForMap(sql.toString(), args.toArray());
        List<HrKpiBoardVO.ChartPoint> points = new ArrayList<>();
        points.add(point("初筛", "转化", toDouble(row.get("screen_cnt"))));
        points.add(point("电话沟通", "转化", toDouble(row.get("phone_cnt"))));
        points.add(point("面试", "转化", toDouble(row.get("interview_cnt"))));
        points.add(point("待入职", "转化", toDouble(row.get("onboard_cnt"))));
        return points;
    }

    private static double toDouble(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private List<HrKpiBoardVO.ChartPoint> buildStageCycle(HrKpiQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.stage_code, s.stage_name, AVG(TIMESTAMPDIFF(DAY, a.submitted_at, e.event_at)) avg_days
                FROM hr_stage_event e
                JOIN hr_application a ON a.id = e.application_id AND a.is_active = 1
                JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                LEFT JOIN hr_stage_def s ON s.stage_code = e.stage_code
                WHERE e.is_active = 1 AND DATE(e.event_at) BETWEEN ? AND ?
                  AND e.stage_code IN ('SCREEN_PASS','PHONE_PASS','INVITED','FIRST_ROUND','FINAL','PENDING_ONBOARD','ONBOARDED')
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(q.getStartDate()));
        args.add(Date.valueOf(q.getEndDate()));
        if (q.getOwnerUserId() != null) {
            sql.append(" AND r.id IN (SELECT requisition_id FROM hr_requisition_owner WHERE user_id = ? AND is_active = 1) ");
            args.add(q.getOwnerUserId());
        }
        dataScope.apply(sql, args, "r", null);
        sql.append(" GROUP BY e.stage_code, s.stage_name ORDER BY MIN(s.sort_no)");
        return jdbc.query(sql.toString(), (rs, i) -> {
            HrKpiBoardVO.ChartPoint p = new HrKpiBoardVO.ChartPoint();
            String name = rs.getString("stage_name");
            p.setAxis(name == null ? rs.getString("stage_code") : name);
            p.setSeries("平均用时(天)");
            p.setValue(round1(rs.getDouble("avg_days")));
            return p;
        }, args.toArray());
    }

    private List<HrKpiBoardVO.RadarPoint> buildRadar(List<HrKpiBoardVO.OwnerRow> owners) {
        List<HrKpiBoardVO.RadarPoint> points = new ArrayList<>();
        List<HrKpiBoardVO.OwnerRow> top = owners.stream().limit(5).toList();
        for (HrKpiBoardVO.OwnerRow o : top) {
            points.add(radar(o.getOwnerName(), "入职人数", o.getOnboardedCount() == null ? 0 : o.getOnboardedCount()));
            points.add(radar(o.getOwnerName(), "按期率%", o.getOnTimeRate() == null ? 0 : o.getOnTimeRate() * 100));
            points.add(radar(o.getOwnerName(), "转化率%", o.getConversionRate() == null ? 0 : o.getConversionRate() * 100));
            double cycleScore = o.getAvgCycleDays() == null ? 0 : Math.max(0, 60 - o.getAvgCycleDays());
            points.add(radar(o.getOwnerName(), "周期得分", cycleScore));
            points.add(radar(o.getOwnerName(), "在招岗位", o.getOpenJobs() == null ? 0 : o.getOpenJobs()));
        }
        return points;
    }

    private List<HrKpiBoardVO.ChartPoint> buildGradeDistribution(List<JobSnap> jobs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("重要性-高", 0);
        map.put("重要性-中", 0);
        map.put("重要性-低", 0);
        map.put("优先级-紧急", 0);
        map.put("优先级-优先", 0);
        map.put("优先级-常规", 0);
        map.put("难度-高", 0);
        map.put("难度-中", 0);
        map.put("难度-低", 0);
        for (JobSnap j : jobs) {
            bump(map, "重要性-" + levelLabel(j.importance));
            bump(map, "优先级-" + priorityLabel(j.priority));
            bump(map, "难度-" + levelLabel(j.difficulty));
        }
        List<HrKpiBoardVO.ChartPoint> points = new ArrayList<>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            String[] parts = e.getKey().split("-", 2);
            points.add(point(parts[1], parts[0], e.getValue().doubleValue()));
        }
        return points;
    }

    private static void bump(Map<String, Integer> map, String key) {
        map.merge(key, 1, Integer::sum);
    }

    private HrKpiBoardVO.JobRow toJobRow(JobSnap j) {
        HrKpiBoardVO.JobRow row = new HrKpiBoardVO.JobRow();
        row.setId(j.id);
        row.setJobName(j.jobName);
        row.setDeptName(j.deptName);
        row.setOwnerNames(j.ownerNames);
        row.setStatus(j.status);
        row.setPriority(j.priority);
        row.setPriorityLabel(priorityLabel(j.priority));
        row.setImportanceLevel(j.importance);
        row.setImportanceLabel(levelLabel(j.importance));
        row.setUrgencyLevel(j.priority);
        row.setUrgencyLabel(priorityLabel(j.priority));
        row.setDifficultyLevel(j.difficulty);
        row.setDifficultyLabel(levelLabel(j.difficulty));
        row.setHeadcount(j.headcount);
        row.setArrived(j.arrived);
        row.setReceivedDate(j.receivedDate);
        row.setTargetDate(j.targetDate);
        row.setOnboardDate(j.onboardDate);
        row.setOverdueDays(j.overdueDays);
        row.setProgressStatus("OPEN".equals(j.status) ? "进行中" : j.status);
        return row;
    }

    private HrKpiBoardVO.OnboardRow toOnboardRow(OnboardSnap o) {
        HrKpiBoardVO.OnboardRow row = new HrKpiBoardVO.OnboardRow();
        row.setApplicationId(o.applicationId);
        row.setCandidateName(o.candidateName);
        row.setJobName(o.jobName);
        row.setOwnerNames(o.ownerNames);
        row.setOnboardDate(o.onboardDate);
        row.setCycleDays(o.cycleDays);
        return row;
    }

    private static HrKpiBoardVO.ChartPoint point(String axis, String series, Double value) {
        HrKpiBoardVO.ChartPoint p = new HrKpiBoardVO.ChartPoint();
        p.setAxis(axis);
        p.setSeries(series);
        p.setValue(value);
        return p;
    }

    private static HrKpiBoardVO.RadarPoint radar(String owner, String metric, double value) {
        HrKpiBoardVO.RadarPoint p = new HrKpiBoardVO.RadarPoint();
        p.setOwnerName(owner);
        p.setMetric(metric);
        p.setValue(round1(value));
        return p;
    }

    private static String bucket(LocalDate day, String grain) {
        return switch (grain) {
            case "day" -> day.toString();
            case "week" -> day.get(WEEK.weekBasedYear()) + "-W" + String.format("%02d", day.get(WEEK.weekOfWeekBasedYear()));
            case "quarter" -> day.getYear() + "-Q" + ((day.getMonthValue() - 1) / 3 + 1);
            default -> day.getYear() + "-" + String.format("%02d", day.getMonthValue());
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
                // fallthrough
            }
        }
        Matcher cn = CN_DATE.matcher(text);
        if (cn.find()) {
            try {
                int year = cn.group(1) == null ? LocalDate.now().getYear() : Integer.parseInt(cn.group(1));
                return LocalDate.of(year, Integer.parseInt(cn.group(2)), Integer.parseInt(cn.group(3)));
            } catch (Exception ignored) {
                // fallthrough
            }
        }
        return null;
    }

    private static List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String part : csv.split(",")) {
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

    private static List<Long> collectOwnerIds(List<JobSnap> jobs, List<OnboardSnap> onboardings) {
        List<Long> ids = new ArrayList<>();
        for (JobSnap job : jobs) {
            ids.addAll(job.ownerIds);
        }
        for (OnboardSnap o : onboardings) {
            ids.addAll(o.ownerIds);
        }
        return ids.stream().distinct().toList();
    }

    private Map<Long, String> loadNicknames(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        jdbc.query("SELECT user_id, nickname FROM sys_user WHERE user_id IN (" + placeholders + ") AND is_active = 1",
                rs -> {
                    map.put(rs.getLong("user_id"), rs.getString("nickname"));
                }, ids.toArray());
        return map;
    }

    /** owner_ids 与 owner_names 同序（ORDER BY sort_no），按索引对齐；再回落昵称表。 */
    private static String resolveOwnerName(Long id, String namesCsv, List<Long> ids, Map<Long, String> nicknameCache) {
        if (id == null) {
            return "未指定";
        }
        if (ids != null && namesCsv != null && !namesCsv.isBlank()) {
            int idx = ids.indexOf(id);
            if (idx >= 0) {
                String[] parts = namesCsv.split("、", -1);
                if (idx < parts.length && StringUtils.hasText(parts[idx])) {
                    return parts[idx].trim();
                }
            }
        }
        String nick = nicknameCache == null ? null : nicknameCache.get(id);
        return StringUtils.hasText(nick) ? nick.trim() : ("用户#" + id);
    }

    private static HrKpiBoardVO.JobRow sanitizeJobOwners(HrKpiBoardVO.JobRow row, List<JobSnap> jobs,
                                                         Long filterOwnerId, Map<Long, String> nicknameCache) {
        if (filterOwnerId == null || row == null) {
            return row;
        }
        JobSnap snap = jobs.stream().filter(j -> j.id == row.getId()).findFirst().orElse(null);
        if (snap != null) {
            row.setOwnerNames(resolveOwnerName(filterOwnerId, snap.ownerNames, snap.ownerIds, nicknameCache));
        } else {
            row.setOwnerNames(resolveOwnerName(filterOwnerId, row.getOwnerNames(), List.of(filterOwnerId), nicknameCache));
        }
        return row;
    }

    private static HrKpiBoardVO.OnboardRow sanitizeOnboardOwners(HrKpiBoardVO.OnboardRow row, OnboardSnap snap,
                                                                Long filterOwnerId, Map<Long, String> nicknameCache) {
        if (filterOwnerId == null || row == null || snap == null) {
            return row;
        }
        row.setOwnerNames(resolveOwnerName(filterOwnerId, snap.ownerNames, snap.ownerIds, nicknameCache));
        return row;
    }

    private static String priorityLabel(Integer p) {
        if (p == null) {
            return "—";
        }
        return switch (p) {
            case 1 -> "紧急";
            case 2 -> "优先";
            case 3 -> "常规";
            default -> String.valueOf(p);
        };
    }

    private static String levelLabel(Integer level) {
        if (level == null) {
            return "未评";
        }
        return switch (level) {
            case 1 -> "高";
            case 2 -> "中";
            case 3 -> "低";
            default -> String.valueOf(level);
        };
    }

    private static String pct(Double rate) {
        if (rate == null) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static void writeRow(Sheet sheet, int rowIdx, String... cols) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < cols.length; i++) {
            row.createCell(i).setCellValue(cols[i] == null ? "" : cols[i]);
        }
    }

    private static class JobSnap {
        long id;
        String jobName;
        String deptName;
        String status;
        Integer priority;
        Integer importance;
        Integer urgency;
        Integer difficulty;
        int headcount;
        int arrived;
        String targetText;
        LocalDate targetDate;
        LocalDate receivedDate;
        LocalDate onboardDate;
        String ownerNames;
        List<Long> ownerIds = new ArrayList<>();
        Integer overdueDays;
    }

    private static class OnboardSnap {
        long applicationId;
        String candidateName;
        String jobName;
        String ownerNames;
        List<Long> ownerIds = new ArrayList<>();
        LocalDate onboardDate;
        Integer cycleDays;
    }
}
