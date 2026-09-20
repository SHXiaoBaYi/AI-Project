package com.base.admin.service;

import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.vo.HrBoardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HrBoardService {

    private final JdbcTemplate jdbc;
    private final HrDataScope dataScope;

    public HrBoardVO board(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        HrBoardVO vo = new HrBoardVO();
        vo.setFunnel(funnel(q));
        vo.setCycle(cycle(q));
        vo.setHc(hc(q));
        vo.setInterview(interview(q));
        return vo;
    }

    public List<HrBoardVO.DrillRow> drill(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        String kind = q.getDrillKind() == null ? "STAGE" : q.getDrillKind();
        if ("HC".equals(kind)) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT a.id application_id, a.requisition_id, c.display_name candidate_name, r.job_name, a.channel_code,
                       d.stage_name, a.submitter_name, a.submitted_at, rd.interviewer_name, rd.interview_at, r.status
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                LEFT JOIN hr_stage_def d ON d.stage_code = a.current_stage
                LEFT JOIN hr_interview_round rd ON rd.application_id = a.id AND rd.is_active = 1 AND rd.round_no = 1
                WHERE a.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if ("STAGE".equals(kind) && q.getStageCode() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM hr_stage_event e WHERE e.application_id = a.id AND e.stage_code = ? AND e.is_active = 1) ");
            args.add(q.getStageCode());
        }
        if ("ROUND".equals(kind) && q.getRoundNo() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM hr_interview_round x WHERE x.application_id = a.id AND x.round_no = ? AND x.is_active = 1) ");
            args.add(q.getRoundNo());
        }
        appendApplicationFilter(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        sql.append(" ORDER BY a.submitted_at DESC, a.id DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, row) -> mapDrill(rs), args.toArray());
    }

    private List<HrBoardVO.FunnelNode> funnel(HrBoardQueryDTO q) {
        List<StageMeta> stages = jdbc.query("""
                SELECT stage_code, stage_name, data_ready FROM hr_stage_def
                WHERE funnel_visible = 1 AND is_active = 1 ORDER BY sort_no
                """, (rs, row) -> new StageMeta(rs.getString(1), rs.getString(2), rs.getInt(3) == 1));
        Map<String, Long> current = countStages(q, q.getStartDate(), q.getEndDate());
        Period previous = shift(q.getStartDate(), q.getEndDate(), false);
        Period year = shift(q.getStartDate(), q.getEndDate(), true);
        Map<String, Long> mom = countStages(q, previous.start, previous.end);
        Map<String, Long> yoy = countStages(q, year.start, year.end);
        List<HrBoardVO.FunnelNode> nodes = new ArrayList<>();
        Long previousCount = null;
        for (StageMeta stage : stages) {
            HrBoardVO.FunnelNode node = new HrBoardVO.FunnelNode();
            node.setStageCode(stage.code);
            node.setStageName(stage.name);
            long count = current.getOrDefault(stage.code, 0L);
            node.setCount(count);
            node.setUncollected(!stage.ready && count == 0);
            if (!node.isUncollected() && previousCount != null && previousCount > 0) {
                node.setConversion(count * 1.0 / previousCount);
            }
            node.setMom(rate(count, mom.getOrDefault(stage.code, 0L)));
            node.setYoy(rate(count, yoy.getOrDefault(stage.code, 0L)));
            if (!node.isUncollected()) {
                previousCount = count;
            }
            nodes.add(node);
        }
        return nodes;
    }

    private Map<String, Long> countStages(HrBoardQueryDTO q, LocalDate start, LocalDate end) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.stage_code, COUNT(DISTINCT a.id) cnt
                FROM hr_application a
                JOIN hr_stage_event e ON e.application_id = a.id AND e.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                WHERE a.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        HrBoardQueryDTO ranged = copy(q);
        ranged.setStartDate(start);
        ranged.setEndDate(end);
        appendApplicationFilter(sql, args, ranged, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        sql.append(" GROUP BY e.stage_code");
        Map<String, Long> map = new HashMap<>();
        jdbc.query(sql.toString(), rs -> {
            map.put(rs.getString("stage_code"), rs.getLong("cnt"));
        }, args.toArray());
        return map;
    }

    private HrBoardVO.CycleBlock cycle(HrBoardQueryDTO q) {
        HrBoardVO.CycleBlock block = new HrBoardVO.CycleBlock();
        StringBuilder avg = new StringBuilder("""
                SELECT AVG(DATEDIFF(r.onboard_date, r.received_date))
                FROM hr_requisition r WHERE r.is_active = 1 AND r.onboard_date IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        appendRequisitionFilter(avg, args, q, "r");
        dataScope.apply(avg, args, "r", null);
        block.setAvgDays(jdbc.queryForObject(avg.toString(), Double.class, args.toArray()));

        block.setScreenToFirstDays(avgHours("""
                SELECT AVG(TIMESTAMPDIFF(HOUR, TIMESTAMP(a.submitted_at), rd.interview_at)) / 24
                FROM hr_application a
                JOIN hr_interview_round rd ON rd.application_id = a.id AND rd.round_no = 1 AND rd.interview_at IS NOT NULL AND rd.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                WHERE a.is_active = 1
                """, q));
        block.setFirstToSecondDays(avgHours("""
                SELECT AVG(TIMESTAMPDIFF(HOUR, r1.interview_at, r2.interview_at)) / 24
                FROM hr_interview_round r1
                JOIN hr_interview_round r2 ON r2.application_id = r1.application_id AND r2.round_no = 2 AND r2.interview_at IS NOT NULL AND r2.is_active = 1
                JOIN hr_application a ON a.id = r1.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                WHERE r1.round_no = 1 AND r1.interview_at IS NOT NULL AND r1.is_active = 1
                """, q));

        StringBuilder byJob = new StringBuilder("""
                SELECT r.job_name, AVG(DATEDIFF(r.onboard_date, r.received_date)) days
                FROM hr_requisition r WHERE r.is_active = 1 AND r.onboard_date IS NOT NULL
                """);
        List<Object> jobArgs = new ArrayList<>();
        appendRequisitionFilter(byJob, jobArgs, q, "r");
        dataScope.apply(byJob, jobArgs, "r", null);
        byJob.append(" GROUP BY r.job_name ORDER BY days DESC");
        block.setByJob(jdbc.query(byJob.toString(), (rs, row) -> {
            HrBoardVO.NamedDays item = new HrBoardVO.NamedDays();
            item.setName(rs.getString("job_name"));
            item.setDays(rs.getDouble("days"));
            return item;
        }, jobArgs.toArray()));
        block.setTrend(cycleTrend(q));
        return block;
    }

    private Double avgHours(String base, HrBoardQueryDTO q) {
        StringBuilder sql = new StringBuilder(base);
        List<Object> args = new ArrayList<>();
        appendApplicationFilter(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        return jdbc.queryForObject(sql.toString(), Double.class, args.toArray());
    }

    private HrBoardVO.HcBlock hc(HrBoardQueryDTO q) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.job_name, r.location_code, r.status, r.priority, r.target_text, r.received_date, r.onboard_date, r.headcount,
                       COALESCE(d.name, '未分配部门') dept_name
                FROM hr_requisition r
                LEFT JOIN hr_department d ON d.id = r.dept_id AND d.is_active = 1
                WHERE r.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        appendRequisitionFilter(sql, args, q, "r");
        dataScope.apply(sql, args, "r", null);
        sql.append(" ORDER BY r.received_date DESC");
        HrBoardVO.HcBlock block = new HrBoardVO.HcBlock();
        Map<String, HrBoardVO.DeptBar> depts = new HashMap<>();
        jdbc.query(sql.toString(), rs -> {
            int headcount = rs.getInt("headcount");
            boolean arrived = rs.getDate("onboard_date") != null;
            String status = rs.getString("status");
            boolean paused = "PAUSED".equals(status);
            boolean inRate = "OPEN".equals(status) || "DONE".equals(status) || "STOPPED".equals(status);
            if (inRate) {
                block.setDemand(block.getDemand() + headcount);
            }
            if (arrived) {
                block.setArrived(block.getArrived() + headcount);
            }
            if ("OPEN".equals(status) && !arrived) {
                block.setGap(block.getGap() + headcount);
            }
            if ("DONE".equals(status) || "STOPPED".equals(status) || "ARCHIVED".equals(status)) {
                block.setClosed(block.getClosed() + 1);
            }
            if (paused) {
                block.setPaused(block.getPaused() + headcount);
            }
            HrBoardVO.HcRow row = new HrBoardVO.HcRow();
            row.setId(rs.getLong("id"));
            row.setJobName(rs.getString("job_name"));
            row.setLocation("XJ".equals(rs.getString("location_code")) ? "新疆" : "上海");
            row.setStatus(status);
            int priority = rs.getInt("priority");
            row.setPriority(rs.wasNull() ? null : priority);
            row.setTargetText(rs.getString("target_text"));
            row.setReceivedDate(rs.getDate("received_date").toLocalDate());
            if (rs.getDate("onboard_date") != null) {
                row.setOnboardDate(rs.getDate("onboard_date").toLocalDate());
            }
            row.setHeadcount(headcount);
            row.setArrived(arrived ? headcount : 0);
            row.setGap("OPEN".equals(status) && !arrived ? headcount : 0);
            row.setWarning("OPEN".equals(status) && !arrived && rs.getString("target_text") != null
                    && rs.getString("target_text").contains("紧急"));
            block.getRows().add(row);
            HrBoardVO.DeptBar bar = depts.computeIfAbsent(rs.getString("dept_name"), name -> {
                HrBoardVO.DeptBar created = new HrBoardVO.DeptBar();
                created.setDeptName(name);
                return created;
            });
            if (inRate) {
                bar.setDemand(bar.getDemand() + headcount);
            }
            if (arrived) {
                bar.setArrived(bar.getArrived() + headcount);
            }
            bar.setGap(bar.getGap() + row.getGap());
        }, args.toArray());
        if (block.getDemand() > 0) {
            block.setCompletionRate(block.getArrived() * 1.0 / block.getDemand());
        }
        block.setByDept(new ArrayList<>(depts.values()));
        block.setTrend(hcTrend(q, block));
        return block;
    }

    private HrBoardVO.InterviewBlock interview(HrBoardQueryDTO q) {
        HrBoardVO.InterviewBlock block = new HrBoardVO.InterviewBlock();
        StringBuilder rounds = new StringBuilder("""
                SELECT rd.round_no, COUNT(1) cnt
                FROM hr_interview_round rd
                JOIN hr_application a ON a.id = rd.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                WHERE rd.is_active = 1 AND rd.interview_at IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        if (q.getStartDate() != null) {
            rounds.append(" AND rd.interview_at >= ? ");
            args.add(q.getStartDate().atStartOfDay());
        }
        if (q.getEndDate() != null) {
            rounds.append(" AND rd.interview_at < ? ");
            args.add(q.getEndDate().plusDays(1).atStartOfDay());
        }
        appendShared(rounds, args, q, "a", "r");
        dataScope.apply(rounds, args, "r", "a");
        rounds.append(" GROUP BY rd.round_no ORDER BY rd.round_no");
        Map<Integer, Long> counts = new HashMap<>();
        jdbc.query(rounds.toString(), rs -> {
            counts.put(rs.getInt("round_no"), rs.getLong("cnt"));
        }, args.toArray());
        block.getRounds().add(round(1, "一面", counts));
        block.getRounds().add(round(2, "二面", counts));
        block.getRounds().add(round(3, "终面", counts));

        StringBuilder people = new StringBuilder("""
                SELECT COALESCE(rd.interviewer_name, '未填') name, COUNT(1) cnt
                FROM hr_interview_round rd
                JOIN hr_application a ON a.id = rd.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                WHERE rd.is_active = 1
                """);
        List<Object> peopleArgs = new ArrayList<>();
        if (q.getStartDate() != null) {
            people.append(" AND rd.interview_at >= ? ");
            peopleArgs.add(q.getStartDate().atStartOfDay());
        }
        if (q.getEndDate() != null) {
            people.append(" AND rd.interview_at < ? ");
            peopleArgs.add(q.getEndDate().plusDays(1).atStartOfDay());
        }
        appendShared(people, peopleArgs, q, "a", "r");
        dataScope.apply(people, peopleArgs, "r", "a");
        people.append(" GROUP BY COALESCE(rd.interviewer_name, '未填') ORDER BY cnt DESC");
        block.setInterviewers(jdbc.query(people.toString(), (rs, row) -> {
            HrBoardVO.NamedDays item = new HrBoardVO.NamedDays();
            item.setName(rs.getString("name"));
            item.setDays((double) rs.getLong("cnt"));
            return item;
        }, peopleArgs.toArray()));
        block.setTrend(interviewTrend(q));
        return block;
    }

    private List<HrBoardVO.ChartPoint> cycleTrend(HrBoardQueryDTO q) {
        String grain = grainOf(q);
        List<String> axes = dateAxes(q.getStartDate(), q.getEndDate(), grain);
        Map<String, double[]> onboard = new HashMap<>();
        StringBuilder onboardSql = new StringBuilder("""
                SELECT r.onboard_date, DATEDIFF(r.onboard_date, r.received_date) days
                FROM hr_requisition r
                WHERE r.is_active = 1 AND r.onboard_date IS NOT NULL AND r.received_date IS NOT NULL
                """);
        List<Object> onboardArgs = new ArrayList<>();
        appendDate(onboardSql, onboardArgs, "r.onboard_date", q);
        appendShared(onboardSql, onboardArgs, q, null, "r");
        dataScope.apply(onboardSql, onboardArgs, "r", null);
        jdbc.query(onboardSql.toString(), rs -> {
            addAvg(onboard, bucket(rs.getDate(1).toLocalDate(), grain), rs.getDouble(2));
        }, onboardArgs.toArray());

        Map<String, double[]> first = new HashMap<>();
        StringBuilder firstSql = new StringBuilder("""
                SELECT DATE(rd.interview_at), TIMESTAMPDIFF(HOUR, TIMESTAMP(a.submitted_at), rd.interview_at) / 24
                FROM hr_application a
                JOIN hr_interview_round rd ON rd.application_id = a.id AND rd.round_no = 1 AND rd.interview_at IS NOT NULL AND rd.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                WHERE a.is_active = 1
                """);
        List<Object> firstArgs = new ArrayList<>();
        appendDateTime(firstSql, firstArgs, "rd.interview_at", q);
        appendShared(firstSql, firstArgs, q, "a", "r");
        dataScope.apply(firstSql, firstArgs, "r", "a");
        jdbc.query(firstSql.toString(), rs -> {
            addAvg(first, bucket(rs.getDate(1).toLocalDate(), grain), rs.getDouble(2));
        }, firstArgs.toArray());

        Map<String, double[]> second = new HashMap<>();
        StringBuilder secondSql = new StringBuilder("""
                SELECT DATE(r2.interview_at), TIMESTAMPDIFF(HOUR, r1.interview_at, r2.interview_at) / 24
                FROM hr_interview_round r1
                JOIN hr_interview_round r2 ON r2.application_id = r1.application_id AND r2.round_no = 2 AND r2.interview_at IS NOT NULL AND r2.is_active = 1
                JOIN hr_application a ON a.id = r1.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                WHERE r1.round_no = 1 AND r1.interview_at IS NOT NULL AND r1.is_active = 1
                """);
        List<Object> secondArgs = new ArrayList<>();
        appendDateTime(secondSql, secondArgs, "r2.interview_at", q);
        appendShared(secondSql, secondArgs, q, "a", "r");
        dataScope.apply(secondSql, secondArgs, "r", "a");
        jdbc.query(secondSql.toString(), rs -> {
            addAvg(second, bucket(rs.getDate(1).toLocalDate(), grain), rs.getDouble(2));
        }, secondArgs.toArray());

        List<HrBoardVO.ChartPoint> points = new ArrayList<>();
        points.addAll(avgSeries(axes, onboard, "平均到岗"));
        points.addAll(avgSeries(axes, first, "创建到一面"));
        points.addAll(avgSeries(axes, second, "一面到二面"));
        return points;
    }

    private List<HrBoardVO.ChartPoint> hcTrend(HrBoardQueryDTO q, HrBoardVO.HcBlock ignored) {
        String grain = grainOf(q);
        List<String> axes = dateAxes(q.getStartDate(), q.getEndDate(), grain);
        Map<String, Double> demand = new HashMap<>();
        Map<String, Double> arrived = new HashMap<>();
        Map<String, Double> gap = new HashMap<>();
        StringBuilder sql = new StringBuilder("""
                SELECT r.received_date, r.onboard_date, r.status, r.headcount
                FROM hr_requisition r
                WHERE r.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (q.getStartDate() != null && q.getEndDate() != null) {
            sql.append(" AND ((r.received_date >= ? AND r.received_date <= ?) OR (r.onboard_date >= ? AND r.onboard_date <= ?)) ");
            args.add(q.getStartDate());
            args.add(q.getEndDate());
            args.add(q.getStartDate());
            args.add(q.getEndDate());
        }
        appendShared(sql, args, q, null, "r");
        dataScope.apply(sql, args, "r", null);
        jdbc.query(sql.toString(), rs -> {
            int headcount = rs.getInt("headcount");
            String status = rs.getString("status");
            LocalDate received = rs.getDate("received_date") == null ? null : rs.getDate("received_date").toLocalDate();
            LocalDate onboard = rs.getDate("onboard_date") == null ? null : rs.getDate("onboard_date").toLocalDate();
            boolean inRate = "OPEN".equals(status) || "DONE".equals(status) || "STOPPED".equals(status);
            if (inRange(received, q) && inRate) {
                demand.merge(bucket(received, grain), (double) headcount, Double::sum);
            }
            if (inRange(onboard, q)) {
                arrived.merge(bucket(onboard, grain), (double) headcount, Double::sum);
            }
            if (inRange(received, q) && "OPEN".equals(status) && onboard == null) {
                gap.merge(bucket(received, grain), (double) headcount, Double::sum);
            }
        }, args.toArray());
        List<HrBoardVO.ChartPoint> points = new ArrayList<>();
        points.addAll(sumSeries(axes, demand, "需求"));
        points.addAll(sumSeries(axes, arrived, "已到岗"));
        points.addAll(sumSeries(axes, gap, "缺口"));
        return points;
    }

    private List<HrBoardVO.ChartPoint> interviewTrend(HrBoardQueryDTO q) {
        String grain = grainOf(q);
        List<String> axes = dateAxes(q.getStartDate(), q.getEndDate(), grain);
        Map<String, Double> first = new HashMap<>();
        Map<String, Double> second = new HashMap<>();
        Map<String, Double> third = new HashMap<>();
        StringBuilder sql = new StringBuilder("""
                SELECT DATE(rd.interview_at) axis_date, rd.round_no
                FROM hr_interview_round rd
                JOIN hr_application a ON a.id = rd.application_id AND a.is_active = 1
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id AND r.is_active = 1
                WHERE rd.is_active = 1 AND rd.interview_at IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        appendDateTime(sql, args, "rd.interview_at", q);
        appendShared(sql, args, q, "a", "r");
        dataScope.apply(sql, args, "r", "a");
        jdbc.query(sql.toString(), rs -> {
            String axis = bucket(rs.getDate("axis_date").toLocalDate(), grain);
            int roundNo = rs.getInt("round_no");
            if (roundNo == 1) {
                first.merge(axis, 1d, Double::sum);
            } else if (roundNo == 2) {
                second.merge(axis, 1d, Double::sum);
            } else if (roundNo == 3) {
                third.merge(axis, 1d, Double::sum);
            }
        }, args.toArray());
        List<HrBoardVO.ChartPoint> points = new ArrayList<>();
        points.addAll(sumSeries(axes, first, "一面"));
        points.addAll(sumSeries(axes, second, "二面"));
        points.addAll(sumSeries(axes, third, "终面"));
        return points;
    }

    private static void addAvg(Map<String, double[]> bag, String axis, double value) {
        double[] cell = bag.computeIfAbsent(axis, key -> new double[2]);
        cell[0] += value;
        cell[1] += 1;
    }

    private static List<HrBoardVO.ChartPoint> avgSeries(List<String> axes, Map<String, double[]> bag, String series) {
        List<HrBoardVO.ChartPoint> points = new ArrayList<>();
        for (String axis : axes) {
            double[] cell = bag.get(axis);
            HrBoardVO.ChartPoint point = new HrBoardVO.ChartPoint();
            point.setAxis(axis);
            point.setSeries(series);
            point.setValue(cell == null || cell[1] == 0 ? 0 : Math.round(cell[0] / cell[1] * 10.0) / 10.0);
            points.add(point);
        }
        return points;
    }

    private static List<HrBoardVO.ChartPoint> sumSeries(List<String> axes, Map<String, Double> bag, String series) {
        List<HrBoardVO.ChartPoint> points = new ArrayList<>();
        for (String axis : axes) {
            HrBoardVO.ChartPoint point = new HrBoardVO.ChartPoint();
            point.setAxis(axis);
            point.setSeries(series);
            point.setValue(bag.getOrDefault(axis, 0d));
            points.add(point);
        }
        return points;
    }

    private static void appendDate(StringBuilder sql, List<Object> args, String column, HrBoardQueryDTO q) {
        if (q.getStartDate() != null) {
            sql.append(" AND ").append(column).append(" >= ? ");
            args.add(q.getStartDate());
        }
        if (q.getEndDate() != null) {
            sql.append(" AND ").append(column).append(" <= ? ");
            args.add(q.getEndDate());
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

    private static boolean inRange(LocalDate date, HrBoardQueryDTO q) {
        if (date == null) {
            return false;
        }
        if (q.getStartDate() != null && date.isBefore(q.getStartDate())) {
            return false;
        }
        return q.getEndDate() == null || !date.isAfter(q.getEndDate());
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

    private static List<String> dateAxes(LocalDate start, LocalDate end, String grain) {
        List<String> axes = new ArrayList<>();
        if (start == null || end == null || end.isBefore(start)) {
            return axes;
        }
        LocalDate cursor = align(start, grain);
        LocalDate last = align(end, grain);
        int guard = 0;
        while (!cursor.isAfter(last) && guard++ < 400) {
            axes.add(bucket(cursor, grain));
            cursor = switch (grain) {
                case "month" -> cursor.plusMonths(1);
                case "year" -> cursor.plusYears(1);
                case "week" -> cursor.plusWeeks(1);
                default -> cursor.plusDays(1);
            };
        }
        return axes;
    }

    private static LocalDate align(LocalDate date, String grain) {
        WeekFields fields = WeekFields.of(Locale.CHINA);
        return switch (grain) {
            case "month" -> date.withDayOfMonth(1);
            case "year" -> date.withDayOfYear(1);
            case "week" -> date.with(fields.dayOfWeek(), 1);
            default -> date;
        };
    }

    private HrBoardVO.RoundCount round(int no, String name, Map<Integer, Long> counts) {
        HrBoardVO.RoundCount item = new HrBoardVO.RoundCount();
        item.setRoundNo(no);
        item.setRoundName(name);
        item.setCount(counts.getOrDefault(no, 0L));
        return item;
    }

    private void appendApplicationFilter(StringBuilder sql, List<Object> args, HrBoardQueryDTO q, String application, String requisition) {
        if (q.getStartDate() != null) {
            sql.append(" AND ").append(application).append(".submitted_at >= ? ");
            args.add(q.getStartDate());
        }
        if (q.getEndDate() != null) {
            sql.append(" AND ").append(application).append(".submitted_at <= ? ");
            args.add(q.getEndDate());
        }
        appendShared(sql, args, q, application, requisition);
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
        if (q.getTargetText() != null && !q.getTargetText().isBlank()) {
            sql.append(" AND TRIM(").append(requisition).append(".target_text) = ? ");
            args.add(q.getTargetText().trim());
        }
    }

    private static HrBoardVO.DrillRow mapDrill(ResultSet rs) throws SQLException {
        HrBoardVO.DrillRow row = new HrBoardVO.DrillRow();
        row.setApplicationId(rs.getLong("application_id"));
        long requisitionId = rs.getLong("requisition_id");
        row.setRequisitionId(rs.wasNull() ? null : requisitionId);
        row.setCandidateName(rs.getString("candidate_name"));
        row.setJobName(rs.getString("job_name"));
        row.setChannel(rs.getString("channel_code"));
        row.setStageName(rs.getString("stage_name"));
        row.setSubmitter(rs.getString("submitter_name"));
        if (rs.getDate("submitted_at") != null) {
            row.setSubmittedAt(rs.getDate("submitted_at").toLocalDate());
        }
        row.setInterviewer(rs.getString("interviewer_name"));
        if (rs.getTimestamp("interview_at") != null) {
            row.setInterviewAt(rs.getTimestamp("interview_at").toLocalDateTime());
        }
        row.setStatus(rs.getString("status"));
        return row;
    }

    private static Double rate(long current, long base) {
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

    private static HrBoardQueryDTO copy(HrBoardQueryDTO q) {
        HrBoardQueryDTO copy = new HrBoardQueryDTO();
        copy.setDeptId(q.getDeptId());
        copy.setLocationCode(q.getLocationCode());
        copy.setChannelCode(q.getChannelCode());
        copy.setRequisitionId(q.getRequisitionId());
        copy.setOwnerUserId(q.getOwnerUserId());
        copy.setPriority(q.getPriority());
        copy.setStatus(q.getStatus());
        return copy;
    }

    private record StageMeta(String code, String name, boolean ready) {
    }

    private record Period(LocalDate start, LocalDate end) {
    }
}
