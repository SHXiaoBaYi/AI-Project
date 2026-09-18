package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.BoardTaskOpsDrillQueryDTO;
import com.base.admin.domain.dto.BoardTaskOpsQueryDTO;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskAssignee;
import com.base.admin.domain.vo.BoardTaskOpsPersonRateVO;
import com.base.admin.domain.vo.BoardTaskOpsRowVO;
import com.base.admin.domain.vo.BoardTaskOpsSummaryVO;
import com.base.admin.mapper.SysTaskAssigneeMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.service.BoardTaskOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardTaskOpsServiceImpl implements BoardTaskOpsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final WeekFields ISO = WeekFields.ISO;
    private static final Set<String> OPEN = Set.of("待分配", "未开始", "待处理", "进行中");

    private final SysTaskMapper taskMapper;
    private final SysTaskAssigneeMapper assigneeMapper;

    @Override
    public BoardTaskOpsSummaryVO summary(BoardTaskOpsQueryDTO query) {
        QueryCtx ctx = resolveCtx(query == null ? new BoardTaskOpsQueryDTO() : query);
        List<SysTask> tasks = loadFilteredTasks(ctx.filterUserIds, ctx.taskTypes);

        long periodDue = 0, periodOverdue = 0, periodDone = 0;
        long onTimeDone = 0, rangeDone = 0, rangeCompleted = 0, rangeTotal = 0;

        for (SysTask t : tasks) {
            String st = nz(t.getStatus(), "未开始");
            boolean open = isOpen(st);
            boolean done = "已完成".equals(st);
            LocalDate planDay = t.getPlanEndTime() == null ? null : t.getPlanEndTime().toLocalDate();
            LocalDate actualDay = resolveActualEndDay(t);
            boolean planInRange = planDay != null && inRange(planDay, ctx.rangeStart, ctx.rangeEnd);
            boolean doneInRange = done && actualDay != null && inRange(actualDay, ctx.rangeStart, ctx.rangeEnd);

            if (open && planInRange) {
                periodDue++;
                if (isOverdue(t, ctx.now)) {
                    periodOverdue++;
                }
            }
            if (doneInRange) {
                periodDone++;
                rangeDone++;
                if (isOnTime(t)) {
                    onTimeDone++;
                }
            }
            // 完成率分母：计划截止落在区间，或本区间内实际完成（没有计划截止的完成单也要算进去）
            if (planInRange || doneInRange) {
                rangeTotal++;
                if (done) {
                    rangeCompleted++;
                }
            }
        }

        BoardTaskOpsSummaryVO vo = new BoardTaskOpsSummaryVO();
        vo.setGrain(ctx.grain);
        vo.setPeriodLabel(periodLabel(ctx.grain));
        vo.setStartDate(ctx.rangeStart.format(DAY));
        vo.setEndDate(ctx.rangeEnd.format(DAY));
        vo.setPeriodDue(periodDue);
        vo.setPeriodOverdue(periodOverdue);
        vo.setPeriodDone(periodDone);
        vo.setOnTimeDone(onTimeDone);
        vo.setRangeDone(rangeDone);
        vo.setOnTimeRate(pct(onTimeDone, rangeDone));
        vo.setRangeCompleted(rangeCompleted);
        vo.setRangeTotal(rangeTotal);
        vo.setCompletionRate(pct(rangeCompleted, rangeTotal));
        return vo;
    }

    @Override
    public PageResult<BoardTaskOpsPersonRateVO> personRate(BoardTaskOpsDrillQueryDTO query) {
        if (query == null || !StringUtils.hasText(query.getMetric())) {
            return new PageResult<>(0L, List.of());
        }
        String metric = query.getMetric().trim();
        if (!"onTimeRate".equals(metric) && !"completionRate".equals(metric)) {
            return new PageResult<>(0L, List.of());
        }
        QueryCtx ctx = resolveCtx(query);
        List<SysTask> tasks = loadFilteredTasks(ctx.filterUserIds, ctx.taskTypes);
        Map<Long, List<SysTaskAssignee>> assigneesByTask = loadAssigneesByTask(
                tasks.stream().map(SysTask::getId).filter(Objects::nonNull).toList());

        Map<Long, Agg> aggMap = new LinkedHashMap<>();
        for (SysTask t : tasks) {
            if ("onTimeRate".equals(metric)) {
                if (!matchMetric(t, "onTimeRate", "all", ctx)) {
                    continue;
                }
                String tag = timingTagOf(t);
                for (PersonRef p : personsOf(t, assigneesByTask)) {
                    if (!personSelected(ctx, p.userId())) {
                        continue;
                    }
                    Agg a = aggMap.computeIfAbsent(p.userId(), k -> new Agg(p.userId(), p.userName()));
                    a.denominator++;
                    if ("ON_TIME".equals(tag) || "EARLY".equals(tag)) {
                        a.numerator++;
                    }
                    if ("ON_TIME".equals(tag)) {
                        a.onTimeCount++;
                    } else if ("EARLY".equals(tag)) {
                        a.earlyCount++;
                    } else if ("LATE".equals(tag)) {
                        a.lateCount++;
                    }
                }
            } else {
                if (!matchMetric(t, "completionRate", "all", ctx)) {
                    continue;
                }
                boolean done = "已完成".equals(nz(t.getStatus(), "未开始"));
                boolean open = isOpen(nz(t.getStatus(), "未开始"));
                for (PersonRef p : personsOf(t, assigneesByTask)) {
                    if (!personSelected(ctx, p.userId())) {
                        continue;
                    }
                    Agg a = aggMap.computeIfAbsent(p.userId(), k -> new Agg(p.userId(), p.userName()));
                    a.denominator++;
                    if (done) {
                        a.numerator++;
                    }
                    if (open) {
                        a.openCount++;
                    }
                }
            }
        }

        List<BoardTaskOpsPersonRateVO> all = aggMap.values().stream()
                .sorted(Comparator
                        .comparingDouble((Agg a) -> pct(a.numerator, a.denominator)).reversed()
                        .thenComparing(Comparator.comparingLong((Agg a) -> a.denominator).reversed())
                        .thenComparing(a -> a.userName, Comparator.nullsLast(String::compareTo)))
                .map(a -> {
                    BoardTaskOpsPersonRateVO vo = new BoardTaskOpsPersonRateVO();
                    vo.setUserId(a.userId);
                    vo.setUserName(a.userName);
                    vo.setNumerator(a.numerator);
                    vo.setDenominator(a.denominator);
                    vo.setRate(pct(a.numerator, a.denominator));
                    if ("onTimeRate".equals(metric)) {
                        vo.setOnTimeCount(a.onTimeCount);
                        vo.setEarlyCount(a.earlyCount);
                        vo.setLateCount(a.lateCount);
                    } else {
                        vo.setOpenCount(a.openCount);
                    }
                    return vo;
                })
                .toList();

        return pageOf(all, query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<BoardTaskOpsRowVO> drill(BoardTaskOpsDrillQueryDTO query) {
        if (query == null || !StringUtils.hasText(query.getMetric())) {
            return new PageResult<>(0L, List.of());
        }
        QueryCtx ctx = resolveCtx(query);
        String metric = query.getMetric().trim();
        String sub = query.getSubFilter() == null ? "all" : query.getSubFilter().trim();
        Long drillPersonUserId = query.getPersonUserId();

        List<SysTask> tasks = loadFilteredTasks(ctx.filterUserIds, ctx.taskTypes);
        Map<Long, List<SysTaskAssignee>> assigneesByTask =
                loadAssigneesByTask(tasks.stream().map(SysTask::getId).filter(Objects::nonNull).toList());

        List<SysTask> matched = new ArrayList<>();
        for (SysTask t : tasks) {
            if (!matchMetric(t, metric, sub, ctx)) {
                continue;
            }
            if (drillPersonUserId != null && !belongsToPerson(t, drillPersonUserId, assigneesByTask)) {
                continue;
            }
            matched.add(t);
        }

        matched.sort(Comparator
                .comparing(SysTask::getPlanEndTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SysTask::getId, Comparator.reverseOrder()));

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        int from = Math.min((pageNum - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());
        List<SysTask> pageTasks = matched.subList(from, to);

        Map<Long, String> assigneeNames = loadAssigneeNames(pageTasks.stream().map(SysTask::getId).toList());
        List<BoardTaskOpsRowVO> rows = pageTasks.stream()
                .map(t -> toRow(t, ctx.now, assigneeNames.getOrDefault(t.getId(), "")))
                .toList();
        return new PageResult<>((long) matched.size(), rows);
    }

    private List<SysTask> loadFilteredTasks(List<Long> filterUserIds, List<String> taskTypes) {
        List<String> types = taskTypes == null ? List.of() : taskTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        List<Long> userIds = filterUserIds == null ? List.of() : filterUserIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        LambdaQueryWrapper<SysTask> w = new LambdaQueryWrapper<SysTask>()
                .ne(SysTask::getStatus, "已取消")
                .in(!types.isEmpty(), SysTask::getTaskType, types)
                .orderByDesc(SysTask::getId);
        List<SysTask> tasks = taskMapper.selectList(w);
        if (userIds.isEmpty()) {
            return tasks;
        }
        Set<Long> wanted = Set.copyOf(userIds);
        Map<Long, List<SysTaskAssignee>> assigneesByTask = loadAssigneesByTask(
                tasks.stream().map(SysTask::getId).filter(Objects::nonNull).toList());
        return tasks.stream()
                .filter(t -> belongsToAny(t, wanted, assigneesByTask))
                .toList();
    }

    private QueryCtx resolveCtx(BoardTaskOpsQueryDTO query) {
        String grain = normalizeGrain(query.getGrain());
        LocalDate today = LocalDate.now();
        LocalDate[] defaults = defaultRange(grain, today);
        LocalDate rangeStart = parseDay(query.getStartDate(), defaults[0]);
        LocalDate rangeEnd = parseDay(query.getEndDate(), defaults[1]);
        if (rangeEnd.isBefore(rangeStart)) {
            LocalDate tmp = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = tmp;
        }
        return new QueryCtx(grain, rangeStart, rangeEnd, today.atTime(LocalTime.now()),
                query.getFilterUserIds(), query.getTaskTypes());
    }

    private QueryCtx resolveCtx(BoardTaskOpsDrillQueryDTO query) {
        BoardTaskOpsQueryDTO q = new BoardTaskOpsQueryDTO();
        q.setStartDate(query.getStartDate());
        q.setEndDate(query.getEndDate());
        q.setGrain(query.getGrain());
        q.setFilterUserIds(query.getFilterUserIds());
        q.setTaskTypes(query.getTaskTypes());
        return resolveCtx(q);
    }

    private static LocalDate[] defaultRange(String grain, LocalDate asOf) {
        return switch (grain) {
            case "day" -> new LocalDate[]{asOf, asOf};
            case "month" -> new LocalDate[]{asOf.withDayOfMonth(1), asOf.withDayOfMonth(asOf.lengthOfMonth())};
            case "year" -> new LocalDate[]{asOf.withDayOfYear(1), asOf.withDayOfYear(asOf.lengthOfYear())};
            default -> new LocalDate[]{asOf.with(ISO.dayOfWeek(), 1), asOf.with(ISO.dayOfWeek(), 7)};
        };
    }

    private static String normalizeGrain(String grain) {
        if (!StringUtils.hasText(grain)) {
            return "week";
        }
        String g = grain.trim().toLowerCase();
        if ("day".equals(g) || "month".equals(g) || "year".equals(g) || "week".equals(g)) {
            return g;
        }
        return "week";
    }

    private static String periodLabel(String grain) {
        return switch (grain) {
            case "day" -> "今日";
            case "month" -> "本月";
            case "year" -> "本年";
            default -> "本周";
        };
    }

    private boolean matchMetric(SysTask t, String metric, String sub, QueryCtx ctx) {
        String st = nz(t.getStatus(), "未开始");
        boolean open = isOpen(st);
        boolean done = "已完成".equals(st);
        LocalDate planDay = t.getPlanEndTime() == null ? null : t.getPlanEndTime().toLocalDate();
        LocalDate actualDay = resolveActualEndDay(t);

        return switch (metric) {
            case "periodDue", "todayDue", "weekDue" ->
                    open && planDay != null && inRange(planDay, ctx.rangeStart, ctx.rangeEnd);
            case "periodOverdue", "todayOverdue", "weekOverdue" ->
                    open && planDay != null && inRange(planDay, ctx.rangeStart, ctx.rangeEnd) && isOverdue(t, ctx.now);
            case "periodDone", "todayDone", "weekDone" -> {
                if (!done || actualDay == null || !inRange(actualDay, ctx.rangeStart, ctx.rangeEnd)) {
                    yield false;
                }
                yield matchTimingSub(t, sub);
            }
            case "onTimeRate" -> {
                if (!done || actualDay == null || !inRange(actualDay, ctx.rangeStart, ctx.rangeEnd)) {
                    yield false;
                }
                yield matchTimingSub(t, sub);
            }
            case "completionRate" -> {
                if (!inCompletionScope(t, planDay, actualDay, done, ctx)) {
                    yield false;
                }
                if ("done".equalsIgnoreCase(sub)) {
                    yield done;
                }
                if ("open".equalsIgnoreCase(sub)) {
                    yield open;
                }
                yield true;
            }
            default -> false;
        };
    }

    private static boolean inCompletionScope(SysTask t, LocalDate planDay, LocalDate actualDay, boolean done, QueryCtx ctx) {
        if ("已取消".equals(nz(t.getStatus(), "未开始"))) {
            return false;
        }
        boolean planInRange = planDay != null && inRange(planDay, ctx.rangeStart, ctx.rangeEnd);
        boolean doneInRange = done && actualDay != null && inRange(actualDay, ctx.rangeStart, ctx.rangeEnd);
        return planInRange || doneInRange;
    }

    private static boolean personSelected(QueryCtx ctx, Long userId) {
        if (ctx.filterUserIds == null || ctx.filterUserIds.isEmpty()) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        long uid = userId;
        return ctx.filterUserIds.stream().anyMatch(id -> id != null && id == uid);
    }

    private static boolean inRange(LocalDate day, LocalDate start, LocalDate end) {
        return day != null && !day.isBefore(start) && !day.isAfter(end);
    }

    private static <T> PageResult<T> pageOf(List<T> all, Integer pageNumRaw, Integer pageSizeRaw) {
        int pageNum = pageNumRaw == null || pageNumRaw < 1 ? 1 : pageNumRaw;
        int pageSize = pageSizeRaw == null || pageSizeRaw < 1 ? 10 : pageSizeRaw;
        int from = Math.min((pageNum - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return new PageResult<>((long) all.size(), all.subList(from, to));
    }

    private record QueryCtx(String grain, LocalDate rangeStart, LocalDate rangeEnd, LocalDateTime now,
                            List<Long> filterUserIds, List<String> taskTypes) {
    }

    private record PersonRef(Long userId, String userName) {
    }

    private static final class Agg {
        final Long userId;
        final String userName;
        long numerator;
        long denominator;
        long onTimeCount;
        long earlyCount;
        long lateCount;
        long openCount;

        Agg(Long userId, String userName) {
            this.userId = userId;
            this.userName = userName;
        }
    }

    private Map<Long, List<SysTaskAssignee>> loadAssigneesByTask(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        List<SysTaskAssignee> list = assigneeMapper.selectList(new LambdaQueryWrapper<SysTaskAssignee>()
                .in(SysTaskAssignee::getTaskId, taskIds)
                .orderByAsc(SysTaskAssignee::getId));
        Map<Long, List<SysTaskAssignee>> map = new LinkedHashMap<>();
        for (SysTaskAssignee a : list) {
            if (a.getTaskId() == null) {
                continue;
            }
            map.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>()).add(a);
        }
        return map;
    }

    private static List<PersonRef> personsOf(SysTask t, Map<Long, List<SysTaskAssignee>> assigneesByTask) {
        LinkedHashMap<Long, String> uniq = new LinkedHashMap<>();
        if (t.getOwnerUserId() != null) {
            String ownerName = StringUtils.hasText(t.getOwnerName()) ? t.getOwnerName() : ("用户" + t.getOwnerUserId());
            uniq.put(t.getOwnerUserId(), ownerName);
        }
        for (SysTaskAssignee a : assigneesByTask.getOrDefault(t.getId(), List.of())) {
            Long uid = a.getUserId() == null ? 0L : a.getUserId();
            String name = StringUtils.hasText(a.getUserName()) ? a.getUserName() : ("用户" + uid);
            uniq.putIfAbsent(uid, name);
        }
        if (uniq.isEmpty()) {
            return List.of(new PersonRef(0L, "未分配"));
        }
        return uniq.entrySet().stream().map(e -> new PersonRef(e.getKey(), e.getValue())).toList();
    }

    private static boolean belongsToAny(SysTask t, Set<Long> userIds, Map<Long, List<SysTaskAssignee>> assigneesByTask) {
        return personsOf(t, assigneesByTask).stream().anyMatch(p -> userIds.contains(p.userId()));
    }

    private static boolean belongsToPerson(SysTask t, Long personUserId, Map<Long, List<SysTaskAssignee>> assigneesByTask) {
        return personsOf(t, assigneesByTask).stream().anyMatch(p -> Objects.equals(p.userId(), personUserId));
    }

    private BoardTaskOpsRowVO toRow(SysTask t, LocalDateTime now, String assigneeNames) {
        BoardTaskOpsRowVO row = new BoardTaskOpsRowVO();
        row.setId(t.getId());
        row.setTitle(t.getTitle());
        row.setTaskType(t.getTaskType());
        row.setStatus(t.getStatus());
        row.setPriority(t.getPriority());
        row.setProgress(t.getProgress());
        row.setOwnerName(t.getOwnerName());
        row.setAssigneeNames(assigneeNames);
        row.setPlanEndTime(t.getPlanEndTime());
        row.setActualEndTime(t.getActualEndTime() != null ? t.getActualEndTime()
                : ("已完成".equals(t.getStatus()) ? t.getUpdateTime() : null));
        boolean overdue = isOverdue(t, now);
        row.setOverdue(overdue);
        if (overdue && t.getPlanEndTime() != null) {
            long days = ChronoUnit.DAYS.between(t.getPlanEndTime().toLocalDate(), now.toLocalDate());
            row.setOverdueDays((int) Math.max(days, 0));
        }
        fillTiming(row, t);
        return row;
    }

    private void fillTiming(BoardTaskOpsRowVO row, SysTask t) {
        if (!"已完成".equals(t.getStatus())) {
            return;
        }
        if (t.getPlanEndTime() == null) {
            LocalDateTime actualAt = resolveActualEndAt(t);
            if (actualAt == null) {
                row.setTimingTag("UNKNOWN");
                row.setTimingLabel("无完成时间");
                return;
            }
            row.setTimingTag("ON_TIME");
            row.setTimingLabel("正常完成");
            return;
        }
        LocalDateTime actualAt = resolveActualEndAt(t);
        if (actualAt == null) {
            row.setTimingTag("UNKNOWN");
            row.setTimingLabel("无完成时间");
            return;
        }
        LocalDate planDay = t.getPlanEndTime().toLocalDate();
        LocalDate actualDay = actualAt.toLocalDate();
        long days = ChronoUnit.DAYS.between(planDay, actualDay);
        row.setTimingDays((int) days);
        if (days < 0) {
            row.setTimingTag("EARLY");
            row.setTimingLabel("提前完成（提前" + Math.abs(days) + "天）");
        } else if (days > 0) {
            row.setTimingTag("LATE");
            row.setTimingLabel("超时完成（超时" + days + "天）");
        } else {
            row.setTimingTag("ON_TIME");
            row.setTimingLabel("正常完成");
        }
    }

    private static boolean matchTimingSub(SysTask t, String sub) {
        if (!StringUtils.hasText(sub) || "all".equalsIgnoreCase(sub)) {
            return true;
        }
        String tag = timingTagOf(t);
        if ("onTime".equalsIgnoreCase(sub)) {
            return "ON_TIME".equals(tag);
        }
        if ("early".equalsIgnoreCase(sub)) {
            return "EARLY".equals(tag);
        }
        if ("late".equalsIgnoreCase(sub)) {
            return "LATE".equals(tag);
        }
        return true;
    }

    private static String timingTagOf(SysTask t) {
        LocalDateTime actualAt = resolveActualEndAt(t);
        if (actualAt == null) {
            return "UNKNOWN";
        }
        if (t.getPlanEndTime() == null) {
            return "ON_TIME";
        }
        long days = ChronoUnit.DAYS.between(t.getPlanEndTime().toLocalDate(), actualAt.toLocalDate());
        if (days < 0) {
            return "EARLY";
        }
        if (days > 0) {
            return "LATE";
        }
        return "ON_TIME";
    }

    private static LocalDateTime resolveActualEndAt(SysTask t) {
        if (t.getActualEndTime() != null) {
            return t.getActualEndTime();
        }
        if ("已完成".equals(t.getStatus()) && t.getUpdateTime() != null) {
            return t.getUpdateTime();
        }
        return null;
    }

    private Map<Long, String> loadAssigneeNames(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        List<SysTaskAssignee> list = assigneeMapper.selectList(new LambdaQueryWrapper<SysTaskAssignee>()
                .in(SysTaskAssignee::getTaskId, taskIds)
                .orderByAsc(SysTaskAssignee::getId));
        Map<Long, List<String>> grouped = new LinkedHashMap<>();
        for (SysTaskAssignee a : list) {
            if (a.getTaskId() == null) {
                continue;
            }
            String name = StringUtils.hasText(a.getUserName()) ? a.getUserName() : ("用户" + a.getUserId());
            grouped.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>()).add(name);
        }
        return grouped.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().filter(Objects::nonNull).distinct().collect(Collectors.joining("、")),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private static boolean isOpen(String status) {
        return OPEN.contains(status);
    }

    private static boolean isOverdue(SysTask t, LocalDateTime now) {
        if (t.getPlanEndTime() == null || !isOpen(nz(t.getStatus(), "未开始"))) {
            return false;
        }
        return t.getPlanEndTime().isBefore(now);
    }

    private static boolean isOnTime(SysTask t) {
        String tag = timingTagOf(t);
        return "ON_TIME".equals(tag) || "EARLY".equals(tag);
    }

    private static LocalDate resolveActualEndDay(SysTask t) {
        LocalDateTime at = resolveActualEndAt(t);
        return at == null ? null : at.toLocalDate();
    }

    private static double pct(long num, long den) {
        if (den <= 0) {
            return 0;
        }
        return Math.round(num * 10000.0 / den) / 100.0;
    }

    private static LocalDate parseDay(String day, LocalDate fallback) {
        if (!StringUtils.hasText(day)) {
            return fallback;
        }
        try {
            return LocalDate.parse(day.trim(), DAY);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String nz(String s, String d) {
        return StringUtils.hasText(s) ? s : d;
    }
}
