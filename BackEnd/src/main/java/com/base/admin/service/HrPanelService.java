package com.base.admin.service;

import com.base.admin.domain.dto.HrPanelDetailQueryDTO;
import com.base.admin.domain.dto.HrPanelQueryDTO;
import com.base.admin.domain.vo.HrPanelDetailVO;
import com.base.admin.domain.vo.HrPanelJobVO;
import com.base.admin.domain.vo.HrPanelPersonVO;
import com.base.admin.domain.vo.HrPanelStatsVO;
import com.base.admin.domain.vo.HrPanelVO;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HrPanelService {

    private static final List<String> PROGRESS_ORDER = List.of(
            "筛选简历", "电话沟通", "约面", "面试", "背调中", "待入职", "已入职");
    private static final DateTimeFormatter OCCURRED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String OWNER_SQL = """
            (SELECT GROUP_CONCAT(DISTINCT COALESCE(u.nickname, ro.alias) ORDER BY ro.sort_no SEPARATOR '、')
               FROM hr_requisition_owner ro
               LEFT JOIN sys_user u ON u.user_id = ro.user_id
               WHERE ro.requisition_id = r.id AND ro.is_active = 1)
            """;

    private final JdbcTemplate jdbc;
    private final HrDataScope dataScope;

    public HrPanelVO panel(HrPanelQueryDTO query) {
        HrPanelQueryDTO q = query == null ? new HrPanelQueryDTO() : query;
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 10 : Math.min(q.getPageSize(), 100);

        DateWindow window = resolveWindow(q);
        log.info("hr panel stats window {} ~ {} grain={}", window.start(), window.end(), q.getGrain());
        // 下方岗位列表与「目前在招」豆腐块同一过滤口径（部门/负责人/状态等），不套日周月窗；
        // 日期窗只影响其它活动指标，以及列表里的「区间进展」汇总
        FilterSql baseFilter = buildFilter(q, null);
        LocalDate today = LocalDate.now();
        long total = countLong("SELECT COUNT(1) " + baseFilter.fromWhere, baseFilter.args);
        List<HrPanelJobVO> rows = queryJobs(baseFilter, today, pageNum, pageSize, true, window);
        HrPanelVO vo = new HrPanelVO();
        vo.setStats(loadStats(baseFilter, window));
        vo.setTotal(total);
        vo.setRows(rows);
        return vo;
    }

    public HrPanelDetailVO detail(HrPanelDetailQueryDTO query) {
        HrPanelDetailQueryDTO q = query == null ? new HrPanelDetailQueryDTO() : query;
        String metric = q.getMetric() == null ? "" : q.getMetric().trim().toUpperCase();
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 10 : Math.min(q.getPageSize(), 100);
        DateWindow window = resolveWindow(q);
        FilterSql baseFilter = buildFilter(q, null);
        LocalDate today = LocalDate.now();
        LocalDateTime from = window.from();
        LocalDateTime to = window.toExclusive();
        String baseReqIds = "SELECT r.id " + baseFilter.fromWhere;
        return switch (metric) {
            // 在招下钻与豆腐块一致：不受日周月窗限制（列表区间进展仍用 window 填）
            case "OPEN_JOB" -> jobDetail("目前在招岗位", metric,
                    new FilterSql(baseFilter.fromWhere() + " AND r.status = 'OPEN' ", baseFilter.args()),
                    pageNum, pageSize, today, window);
            case "BG_CHECK" -> stageReachedDetail("背调中", metric, baseFilter, baseReqIds, "BG_CHECK", from, to, pageNum, pageSize);
            case "PENDING_ONBOARD" -> stageReachedDetail("待入职", metric, baseFilter, baseReqIds, "PENDING_ONBOARD", from, to, pageNum, pageSize);
            case "TODAY_SCREEN" -> todayPeopleDetail("筛选简历", metric, baseFilter, baseReqIds, from, to, pageNum, pageSize, """
                    SELECT ax.id application_id,
                           MAX(ax.create_time) occurred_at,
                           '新增' last_code
                    FROM hr_application ax
                    WHERE ax.is_active = 1 AND ax.requisition_id IN (%s)
                      AND ax.create_time >= ? AND ax.create_time < ?
                    GROUP BY ax.id
                    """, "'新增'");
            case "TODAY_PHONE" -> todayPeopleDetail("电话沟通", metric, baseFilter, baseReqIds, from, to, pageNum, pageSize, """
                    SELECT p.application_id,
                           MAX(p.called_at) occurred_at,
                           SUBSTRING_INDEX(GROUP_CONCAT(IFNULL(p.result,'') ORDER BY p.called_at DESC, p.id DESC), ',', 1) last_code
                    FROM hr_phone_screen p
                    JOIN hr_application ax ON ax.id = p.application_id AND ax.is_active = 1
                    WHERE p.is_active = 1 AND ax.requisition_id IN (%s)
                      AND p.called_at >= ? AND p.called_at < ?
                    GROUP BY p.application_id
                    """, "CASE x.last_code WHEN 'PASS' THEN '通过' WHEN 'FAIL' THEN '不通过' ELSE IFNULL(NULLIF(x.last_code,''),'已沟通') END");
            case "TODAY_INVITE" -> todayPeopleDetail("约面", metric, baseFilter, baseReqIds, from, to, pageNum, pageSize, """
                    SELECT i.application_id,
                           MAX(COALESCE(i.create_time, i.interview_at)) occurred_at,
                           '已约面' last_code
                    FROM hr_interview_invite i
                    JOIN hr_application ax ON ax.id = i.application_id AND ax.is_active = 1
                    WHERE i.is_active = 1 AND i.status <> 'CANCELLED' AND ax.requisition_id IN (%s)
                      AND COALESCE(i.create_time, i.interview_at) >= ?
                      AND COALESCE(i.create_time, i.interview_at) < ?
                    GROUP BY i.application_id
                    """, "'已约面'");
            case "TODAY_INTERVIEW" -> todayPeopleDetail("实际面试", metric, baseFilter, baseReqIds, from, to, pageNum, pageSize, """
                    SELECT rec.application_id,
                           MAX(COALESCE(rec.interviewed_at, rec.create_time)) occurred_at,
                           SUBSTRING_INDEX(GROUP_CONCAT(IFNULL(rec.conclusion,'') ORDER BY COALESCE(rec.interviewed_at, rec.create_time) DESC, rec.id DESC), ',', 1) last_code
                    FROM hr_interview_record rec
                    JOIN hr_application ax ON ax.id = rec.application_id AND ax.is_active = 1
                    WHERE rec.is_active = 1 AND ax.requisition_id IN (%s)
                      AND COALESCE(rec.interviewed_at, rec.create_time) >= ?
                      AND COALESCE(rec.interviewed_at, rec.create_time) < ?
                    GROUP BY rec.application_id
                    """, "CASE x.last_code WHEN 'PASS' THEN '通过' WHEN 'FAIL' THEN '不通过' ELSE IFNULL(NULLIF(x.last_code,''),'已面试') END");
            case "TODAY_INTERVIEW_PASS" -> todayPeopleDetail("通过面试", metric, baseFilter, baseReqIds, from, to, pageNum, pageSize, """
                    SELECT rec.application_id,
                           MAX(COALESCE(rec.interviewed_at, rec.create_time)) occurred_at,
                           '通过' last_code
                    FROM hr_interview_record rec
                    JOIN hr_application ax ON ax.id = rec.application_id AND ax.is_active = 1
                    WHERE rec.is_active = 1 AND rec.conclusion = 'PASS' AND ax.requisition_id IN (%s)
                      AND COALESCE(rec.interviewed_at, rec.create_time) >= ?
                      AND COALESCE(rec.interviewed_at, rec.create_time) < ?
                    GROUP BY rec.application_id
                    """, "'通过'");
            default -> throw new BusinessException("不支持的面板指标");
        };
    }

    private HrPanelDetailVO jobDetail(String title, String metric, FilterSql filter, int pageNum, int pageSize,
                                      LocalDate today, DateWindow window) {
        long total = countLong("SELECT COUNT(1) " + filter.fromWhere(), filter.args());
        List<HrPanelJobVO> jobs = queryJobs(filter, today, pageNum, pageSize, true, window);
        HrPanelDetailVO vo = new HrPanelDetailVO();
        vo.setMetric(metric);
        vo.setTitle(title);
        vo.setKind("JOB");
        vo.setTotal(total);
        vo.setJobs(jobs);
        return vo;
    }

    /** 区间内到达某阶段的人（阶段事件 ∪ 当前阶段且更新时间在区间）。 */
    private HrPanelDetailVO stageReachedDetail(String title, String metric, FilterSql filter, String reqIds,
                                               String stage, LocalDateTime from, LocalDateTime to,
                                               int pageNum, int pageSize) {
        String fromSql = """
                FROM (
                  SELECT u.application_id, MAX(u.occurred_at) occurred_at
                  FROM (
                    SELECT e.application_id, COALESCE(e.event_at, e.create_time) occurred_at
                    FROM hr_stage_event e
                    JOIN hr_application ax ON ax.id = e.application_id AND ax.is_active = 1
                    WHERE e.is_active = 1 AND e.stage_code = ? AND ax.requisition_id IN (%s)
                      AND COALESCE(e.event_at, e.create_time) >= ? AND COALESCE(e.event_at, e.create_time) < ?
                    UNION ALL
                    SELECT a2.id application_id, a2.update_time occurred_at
                    FROM hr_application a2
                    WHERE a2.is_active = 1 AND a2.current_stage = ? AND a2.requisition_id IN (%s)
                      AND a2.update_time >= ? AND a2.update_time < ?
                  ) u
                  GROUP BY u.application_id
                ) x
                JOIN hr_application a ON a.id = x.application_id AND a.is_active = 1
                JOIN hr_candidate c ON c.id = a.candidate_id AND c.is_active = 1
                JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN hr_department d ON d.id = r.dept_id AND d.is_active = 1
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage AND s.is_active = 1
                """.formatted(reqIds, reqIds);
        List<Object> args = new ArrayList<>();
        args.add(stage);
        args.addAll(filter.args());
        args.add(from);
        args.add(to);
        args.add(stage);
        args.addAll(filter.args());
        args.add(from);
        args.add(to);
        String select = """
                SELECT a.id application_id, c.display_name candidate_name, r.job_name, d.name dept_name,
                       COALESCE(s.stage_name, a.current_stage) stage_name,
                       %s owner_name,
                       x.occurred_at,
                       '%s' result_label
                """.formatted(OWNER_SQL, title);
        return peopleDetail(title, metric, select, fromSql, args, pageNum, pageSize);
    }

    private HrPanelDetailVO todayPeopleDetail(String title, String metric, FilterSql filter, String reqIds,
                                              LocalDateTime from, LocalDateTime to, int pageNum, int pageSize,
                                              String innerSql, String resultExpr) {
        String fromSql = """
                FROM (%s) x
                JOIN hr_application a ON a.id = x.application_id AND a.is_active = 1
                JOIN hr_candidate c ON c.id = a.candidate_id AND c.is_active = 1
                JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN hr_department d ON d.id = r.dept_id AND d.is_active = 1
                LEFT JOIN hr_stage_def s ON s.stage_code = a.current_stage AND s.is_active = 1
                """.formatted(innerSql.formatted(reqIds));
        List<Object> args = new ArrayList<>(filter.args());
        args.add(from);
        args.add(to);
        String select = """
                SELECT a.id application_id, c.display_name candidate_name, r.job_name, d.name dept_name,
                       COALESCE(s.stage_name, a.current_stage) stage_name,
                       %s owner_name,
                       x.occurred_at,
                       %s result_label
                """.formatted(OWNER_SQL, resultExpr);
        return peopleDetail(title, metric, select, fromSql, args, pageNum, pageSize);
    }

    private HrPanelDetailVO peopleDetail(String title, String metric, String select, String fromWhere, List<Object> args,
                                         int pageNum, int pageSize) {
        long total = countLong("SELECT COUNT(1) " + fromWhere, args);
        String listSql = select + fromWhere + " ORDER BY occurred_at DESC, application_id DESC LIMIT ? OFFSET ? ";
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add((long) (pageNum - 1) * pageSize);
        List<HrPanelPersonVO> people = jdbc.query(listSql, (rs, i) -> {
            HrPanelPersonVO row = new HrPanelPersonVO();
            row.setApplicationId(rs.getLong("application_id"));
            row.setCandidateName(rs.getString("candidate_name"));
            row.setJobName(rs.getString("job_name"));
            row.setDeptName(rs.getString("dept_name"));
            row.setOwnerName(rs.getString("owner_name"));
            row.setStageName(rs.getString("stage_name"));
            Timestamp ts = rs.getTimestamp("occurred_at");
            row.setOccurredAt(ts == null ? null : ts.toLocalDateTime().format(OCCURRED_FMT));
            row.setResultLabel(rs.getString("result_label"));
            return row;
        }, listArgs.toArray());
        HrPanelDetailVO vo = new HrPanelDetailVO();
        vo.setMetric(metric);
        vo.setTitle(title);
        vo.setKind("PERSON");
        vo.setTotal(total);
        vo.setPeople(people);
        return vo;
    }

    private List<HrPanelJobVO> queryJobs(FilterSql filter, LocalDate today, int pageNum, int pageSize,
                                         boolean withProgress, DateWindow window) {
        String listSql = """
                SELECT r.id, r.job_name, r.headcount, r.status, r.received_date, r.onboard_date, r.location_code,
                       r.job_priority_code, d.name dept_name,
                       (SELECT jp.code FROM hr_base_dict jp
                         WHERE jp.dict_type = 'JOB_PRIORITY' AND jp.is_active = 1
                           AND jp.code = r.job_priority_code LIMIT 1) priority_code,
                       (SELECT jp.name FROM hr_base_dict jp
                         WHERE jp.dict_type = 'JOB_PRIORITY' AND jp.is_active = 1
                           AND jp.code = r.job_priority_code LIMIT 1) priority_name,
                       (SELECT wa.name FROM hr_base_dict wa
                         WHERE wa.id = r.work_address_id AND wa.dict_type = 'WORK_ADDRESS' AND wa.is_active = 1
                         LIMIT 1) work_address_name,
                       %s owner_name
                """.formatted(OWNER_SQL) + filter.fromWhere() + """
                 ORDER BY FIELD(r.status,'OPEN','PAUSED','DONE','STOPPED','ARCHIVED'), r.received_date DESC, r.id DESC
                 LIMIT ? OFFSET ?
                """;
        List<Object> listArgs = new ArrayList<>(filter.args());
        listArgs.add(pageSize);
        listArgs.add((long) (pageNum - 1) * pageSize);
        List<HrPanelJobVO> rows = jdbc.query(listSql, (rs, i) -> {
            HrPanelJobVO row = new HrPanelJobVO();
            long id = rs.getLong("id");
            row.setId(id);
            String status = rs.getString("status");
            boolean open = "OPEN".equals(status);
            row.setPanelStatus(open ? "OPEN" : "CLOSED");
            row.setPanelStatusLabel(open ? "招聘中" : "已关闭");
            row.setDeptName(rs.getString("dept_name"));
            row.setJobName(rs.getString("job_name"));
            int hc = rs.getInt("headcount");
            row.setHeadcount(rs.wasNull() ? null : hc);
            row.setOwnerName(rs.getString("owner_name"));
            String code = rs.getString("priority_code");
            if (!StringUtils.hasText(code)) {
                code = rs.getString("job_priority_code");
            }
            row.setJobPriorityCode(blankToNull(code));
            String pname = rs.getString("priority_name");
            if (StringUtils.hasText(code) && StringUtils.hasText(pname)) {
                row.setJobPriorityLabel(code + " " + pname);
            } else if (StringUtils.hasText(code)) {
                row.setJobPriorityLabel(code);
            }
            String addr = rs.getString("work_address_name");
            if (!StringUtils.hasText(addr)) {
                String loc = rs.getString("location_code");
                addr = "XJ".equals(loc) ? "新疆" : ("SH".equals(loc) ? "上海" : null);
            }
            row.setWorkLocation(addr);
            LocalDate received = dateOf(rs.getDate("received_date"));
            row.setReceivedDate(received == null ? null : received.toString());
            LocalDate deadline = dateOf(rs.getDate("onboard_date"));
            row.setDeadlineDate(deadline == null ? null : deadline.toString());
            LocalDate end = today;
            if (("DONE".equals(status) || "STOPPED".equals(status) || "ARCHIVED".equals(status)) && deadline != null) {
                end = deadline;
            }
            if (received != null) {
                row.setRecruitingDays((int) Math.max(0, ChronoUnit.DAYS.between(received, end)));
            }
            return row;
        }, listArgs.toArray());
        if (withProgress) {
            fillProgress(rows, window);
        }
        return rows;
    }

    private HrPanelStatsVO loadStats(FilterSql baseFilter, DateWindow window) {
        LocalDateTime from = window.from();
        LocalDateTime to = window.toExclusive();
        String reqIds = "SELECT r.id " + baseFilter.fromWhere;
        HrPanelStatsVO stats = new HrPanelStatsVO();
        // 目前在招 = 当前 OPEN 需求条数（部门/负责人等筛选生效，不绑日期窗）
        stats.setOpenJobCount(countInt(
                "SELECT COUNT(1) " + baseFilter.fromWhere + " AND r.status = 'OPEN'", baseFilter.args));
        stats.setBgCheckCount(countStageReached(baseFilter.args, reqIds, "BG_CHECK", from, to));
        stats.setPendingOnboardCount(countStageReached(baseFilter.args, reqIds, "PENDING_ONBOARD", from, to));
        // 区间内筛选简历 = 区间内新增候选人（投递记录）
        stats.setTodayScreenCount(countDistinctApp(baseFilter.args, """
                SELECT COUNT(DISTINCT a.id)
                FROM hr_application a
                WHERE a.is_active = 1 AND a.requisition_id IN (%s)
                  AND a.create_time >= ? AND a.create_time < ?
                """.formatted(reqIds), from, to));
        stats.setTodayPhoneCount(countDistinctApp(baseFilter.args, """
                SELECT COUNT(DISTINCT a.id)
                FROM hr_application a
                JOIN hr_phone_screen p ON p.application_id = a.id AND p.is_active = 1
                WHERE a.is_active = 1 AND a.requisition_id IN (%s)
                  AND p.called_at >= ? AND p.called_at < ?
                """.formatted(reqIds), from, to));
        stats.setTodayInviteCount(countDistinctApp(baseFilter.args, """
                SELECT COUNT(DISTINCT a.id)
                FROM hr_application a
                JOIN hr_interview_invite i ON i.application_id = a.id AND i.is_active = 1
                WHERE a.is_active = 1 AND a.requisition_id IN (%s)
                  AND i.status <> 'CANCELLED'
                  AND COALESCE(i.create_time, i.interview_at) >= ?
                  AND COALESCE(i.create_time, i.interview_at) < ?
                """.formatted(reqIds), from, to));
        stats.setTodayInterviewCount(countDistinctApp(baseFilter.args, """
                SELECT COUNT(DISTINCT a.id)
                FROM hr_application a
                JOIN hr_interview_record rec ON rec.application_id = a.id AND rec.is_active = 1
                WHERE a.is_active = 1 AND a.requisition_id IN (%s)
                  AND COALESCE(rec.interviewed_at, rec.create_time) >= ?
                  AND COALESCE(rec.interviewed_at, rec.create_time) < ?
                """.formatted(reqIds), from, to));
        stats.setTodayInterviewPassCount(countDistinctApp(baseFilter.args, """
                SELECT COUNT(DISTINCT a.id)
                FROM hr_application a
                JOIN hr_interview_record rec ON rec.application_id = a.id AND rec.is_active = 1
                WHERE a.is_active = 1 AND a.requisition_id IN (%s)
                  AND rec.conclusion = 'PASS'
                  AND COALESCE(rec.interviewed_at, rec.create_time) >= ?
                  AND COALESCE(rec.interviewed_at, rec.create_time) < ?
                """.formatted(reqIds), from, to));
        return stats;
    }

    /**
     * 区间内进入某阶段人数：优先阶段事件；若无事件则回退「当前阶段=该阶段且更新时间在区间」。
     */
    private int countStageReached(List<Object> reqArgs, String reqIdsSql, String stage,
                                  LocalDateTime from, LocalDateTime to) {
        List<Object> args = new ArrayList<>();
        args.add(stage);
        args.addAll(reqArgs);
        args.add(from);
        args.add(to);
        args.add(stage);
        args.addAll(reqArgs);
        args.add(from);
        args.add(to);
        return countInt("""
                SELECT COUNT(DISTINCT application_id) FROM (
                  SELECT e.application_id
                  FROM hr_stage_event e
                  JOIN hr_application a ON a.id = e.application_id AND a.is_active = 1
                  WHERE e.is_active = 1 AND e.stage_code = ?
                    AND a.requisition_id IN (%s)
                    AND COALESCE(e.event_at, e.create_time) >= ?
                    AND COALESCE(e.event_at, e.create_time) < ?
                  UNION
                  SELECT a.id application_id
                  FROM hr_application a
                  WHERE a.is_active = 1 AND a.current_stage = ?
                    AND a.requisition_id IN (%s)
                    AND a.update_time >= ? AND a.update_time < ?
                ) t
                """.formatted(reqIdsSql, reqIdsSql), args);
    }

    private void fillProgress(List<HrPanelJobVO> rows, DateWindow window) {
        if (rows.isEmpty()) {
            return;
        }
        String placeholders = rows.stream().map(r -> "?").collect(Collectors.joining(","));
        List<Object> ids = rows.stream().map(HrPanelJobVO::getId).collect(Collectors.toList());
        Map<Long, LinkedHashMap<String, Integer>> byReq = new LinkedHashMap<>();
        List<Object> eventArgs = new ArrayList<>(ids);
        eventArgs.add(window.from());
        eventArgs.add(window.toExclusive());
        jdbc.query("""
                SELECT a.requisition_id, e.stage_code, COALESCE(s.stage_name, e.stage_code) stage_name, COUNT(DISTINCT a.id) cnt
                FROM hr_stage_event e
                JOIN hr_application a ON a.id = e.application_id AND a.is_active = 1
                LEFT JOIN hr_stage_def s ON s.stage_code = e.stage_code AND s.is_active = 1
                WHERE e.is_active = 1 AND a.requisition_id IN (%s)
                  AND COALESCE(e.event_at, e.create_time) >= ? AND COALESCE(e.event_at, e.create_time) < ?
                GROUP BY a.requisition_id, e.stage_code, COALESCE(s.stage_name, e.stage_code), COALESCE(s.sort_no, 999)
                ORDER BY a.requisition_id, COALESCE(s.sort_no, 999)
                """.formatted(placeholders), rs -> {
            long reqId = rs.getLong("requisition_id");
            String name = displayStage(rs.getString("stage_code"), rs.getString("stage_name"));
            int cnt = rs.getInt("cnt");
            if (cnt > 0 && StringUtils.hasText(name)) {
                byReq.computeIfAbsent(reqId, k -> new LinkedHashMap<>()).merge(name, cnt, Integer::sum);
            }
        }, eventArgs.toArray());
        List<Object> inviteArgs = new ArrayList<>(ids);
        inviteArgs.add(window.from());
        inviteArgs.add(window.toExclusive());
        jdbc.query("""
                SELECT a.requisition_id, COUNT(DISTINCT a.id) cnt
                FROM hr_interview_invite i
                JOIN hr_application a ON a.id = i.application_id AND a.is_active = 1
                WHERE i.is_active = 1 AND i.status <> 'CANCELLED' AND a.requisition_id IN (%s)
                  AND COALESCE(i.create_time, i.interview_at) >= ?
                  AND COALESCE(i.create_time, i.interview_at) < ?
                GROUP BY a.requisition_id
                """.formatted(placeholders), rs -> {
            long reqId = rs.getLong("requisition_id");
            int cnt = rs.getInt("cnt");
            if (cnt > 0) {
                byReq.computeIfAbsent(reqId, k -> new LinkedHashMap<>()).put("约面", cnt);
            }
        }, inviteArgs.toArray());
        for (HrPanelJobVO row : rows) {
            row.setProgressSummary(formatProgress(byReq.get(row.getId())));
        }
    }

    private static String formatProgress(LinkedHashMap<String, Integer> stages) {
        if (stages == null || stages.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (String key : PROGRESS_ORDER) {
            Integer cnt = stages.remove(key);
            if (cnt != null && cnt > 0) {
                parts.add(key + cnt + "人");
            }
        }
        for (Map.Entry<String, Integer> e : stages.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0 && StringUtils.hasText(e.getKey())) {
                parts.add(e.getKey() + e.getValue() + "人");
            }
        }
        return parts.isEmpty() ? "无" : String.join("，", parts);
    }

    private static String displayStage(String code, String name) {
        if ("INVITED".equals(code)) {
            return null;
        }
        if ("SCREEN_PASS".equals(code) || "SCREEN_FAIL".equals(code)) {
            return "筛选简历";
        }
        if ("PHONE_PASS".equals(code) || "PHONE_FAIL".equals(code)) {
            return "电话沟通";
        }
        if ("SHOW_UP".equals(code) || "FIRST_ROUND".equals(code) || "SECOND_ROUND".equals(code) || "FINAL".equals(code)) {
            return "面试";
        }
        if ("BG_CHECK".equals(code)) {
            return "背调中";
        }
        if ("PENDING_ONBOARD".equals(code)) {
            return "待入职";
        }
        if ("ONBOARDED".equals(code)) {
            return "已入职";
        }
        return name;
    }

    /**
     * @param window 保留参数以兼容旧调用；面板主列表与在招统计均传 null（不按日期裁岗位）。
     *               日期窗仅用于活动指标与「区间进展」。
     */
    private FilterSql buildFilter(HrPanelQueryDTO q, DateWindow window) {
        StringBuilder sql = new StringBuilder("""
                FROM hr_requisition r
                LEFT JOIN hr_department d ON d.id = r.dept_id AND d.is_active = 1
                WHERE r.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if ("OPEN".equalsIgnoreCase(blankToNull(q.getPanelStatus()))) {
            sql.append(" AND r.status = 'OPEN' ");
        } else if ("CLOSED".equalsIgnoreCase(blankToNull(q.getPanelStatus()))) {
            sql.append(" AND r.status IN ('DONE','STOPPED','ARCHIVED','PAUSED') ");
        }
        if (q.getDeptId() != null) {
            sql.append("""
                     AND r.dept_id IN (
                       SELECT id FROM hr_department
                       WHERE is_active = 1 AND (id = ? OR CONCAT(',', ancestors, ',') LIKE CONCAT('%,', ?, ',%'))
                     )
                    """);
            args.add(q.getDeptId());
            args.add(String.valueOf(q.getDeptId()));
        }
        if (StringUtils.hasText(q.getJobName())) {
            sql.append(" AND r.job_name LIKE ? ");
            args.add("%" + q.getJobName().trim() + "%");
        }
        HrOwnerFilters.append(sql, args, "r", q.getOwnerUserId(), q.getOwnerUserIds());
        if (StringUtils.hasText(q.getJobPriorityCode())) {
            sql.append(" AND r.job_priority_code = ? ");
            args.add(q.getJobPriorityCode().trim());
        }
        if (q.getWorkAddressId() != null) {
            sql.append("""
                     AND (
                       r.work_address_id = ?
                       OR (
                         r.work_address_id IS NULL
                         AND r.location_code = (
                           SELECT CASE
                             WHEN name LIKE '%新疆%' THEN 'XJ'
                             WHEN name LIKE '%上海%' THEN 'SH'
                             ELSE NULL
                           END
                           FROM hr_base_dict
                           WHERE id = ? AND dict_type = 'WORK_ADDRESS' AND is_active = 1
                         )
                       )
                     )
                    """);
            args.add(q.getWorkAddressId());
            args.add(q.getWorkAddressId());
        }
        if (window != null) {
            // 岗位列表：区间内有招聘活动，或岗位接收日落在区间（无接收日看创建日）
            appendJobDateWindow(sql, args, window);
        }
        dataScope.apply(sql, args, "r", null);
        return new FilterSql(sql.toString(), args);
    }

    private static void appendJobDateWindow(StringBuilder sql, List<Object> args, DateWindow window) {
        LocalDateTime from = window.from();
        LocalDateTime to = window.toExclusive();
        sql.append("""
                 AND (
                   (r.received_date IS NOT NULL AND r.received_date >= ? AND r.received_date <= ?)
                   OR (r.received_date IS NULL AND DATE(r.create_time) >= ? AND DATE(r.create_time) <= ?)
                   OR EXISTS (
                     SELECT 1 FROM hr_application a
                     WHERE a.is_active = 1 AND a.requisition_id = r.id
                       AND a.create_time >= ? AND a.create_time < ?
                   )
                   OR EXISTS (
                     SELECT 1 FROM hr_phone_screen p
                     JOIN hr_application a ON a.id = p.application_id AND a.is_active = 1
                     WHERE p.is_active = 1 AND a.requisition_id = r.id
                       AND p.called_at >= ? AND p.called_at < ?
                   )
                   OR EXISTS (
                     SELECT 1 FROM hr_interview_invite i
                     JOIN hr_application a ON a.id = i.application_id AND a.is_active = 1
                     WHERE i.is_active = 1 AND i.status <> 'CANCELLED' AND a.requisition_id = r.id
                       AND COALESCE(i.create_time, i.interview_at) >= ?
                       AND COALESCE(i.create_time, i.interview_at) < ?
                   )
                   OR EXISTS (
                     SELECT 1 FROM hr_interview_record rec
                     JOIN hr_application a ON a.id = rec.application_id AND a.is_active = 1
                     WHERE rec.is_active = 1 AND a.requisition_id = r.id
                       AND COALESCE(rec.interviewed_at, rec.create_time) >= ?
                       AND COALESCE(rec.interviewed_at, rec.create_time) < ?
                   )
                   OR EXISTS (
                     SELECT 1 FROM hr_stage_event e
                     JOIN hr_application a ON a.id = e.application_id AND a.is_active = 1
                     WHERE e.is_active = 1 AND a.requisition_id = r.id
                       AND COALESCE(e.event_at, e.create_time) >= ?
                       AND COALESCE(e.event_at, e.create_time) < ?
                   )
                 )
                """);
        args.add(window.start());
        args.add(window.end());
        args.add(window.start());
        args.add(window.end());
        for (int i = 0; i < 5; i++) {
            args.add(from);
            args.add(to);
        }
    }

    /** 未传/解析失败时默认当天。 */
    private static DateWindow resolveWindow(HrPanelQueryDTO q) {
        LocalDate today = LocalDate.now();
        LocalDate start = parseDay(q.getStartDate(), today);
        LocalDate end = parseDay(q.getEndDate(), start);
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return new DateWindow(start, end);
    }

    private static LocalDate parseDay(String raw, LocalDate fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        String text = raw.trim();
        if (text.length() >= 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text, DAY_FMT);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException ignored) {
                return fallback;
            }
        }
    }

    private record DateWindow(LocalDate start, LocalDate end) {
        LocalDateTime from() {
            return start.atStartOfDay();
        }

        LocalDateTime toExclusive() {
            return end.plusDays(1).atStartOfDay();
        }
    }

    private int countDistinctApp(List<Object> reqArgs, String sql, Object from, Object to) {
        List<Object> args = new ArrayList<>(reqArgs);
        args.add(from);
        args.add(to);
        return countInt(sql, args);
    }

    private int countInt(String sql, List<Object> args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args.toArray());
        return n == null ? 0 : n;
    }

    private long countLong(String sql, List<Object> args) {
        Long n = jdbc.queryForObject(sql, Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    private static LocalDate dateOf(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record FilterSql(String fromWhere, List<Object> args) {}
}
