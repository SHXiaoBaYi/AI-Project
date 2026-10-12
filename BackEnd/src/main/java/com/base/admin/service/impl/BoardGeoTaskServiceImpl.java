package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.BoardGeoTaskAggQueryDTO;
import com.base.admin.domain.dto.BoardGeoTaskDrillQueryDTO;
import com.base.admin.domain.dto.BoardGeoTaskQueryDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskAssignee;
import com.base.admin.domain.entity.SysTaskType;
import com.base.admin.domain.vo.BoardGeoTaskAggRowVO;
import com.base.admin.domain.vo.BoardGeoTaskRowVO;
import com.base.admin.domain.vo.BoardGeoTaskSummaryVO;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.SysTaskAssigneeMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.mapper.SysTaskTypeMapper;
import com.base.admin.service.BoardGeoTaskService;
import com.base.admin.service.DataScopeFilter;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardGeoTaskServiceImpl implements BoardGeoTaskService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final WeekFields ISO = WeekFields.ISO;
    private static final Set<String> OPEN = Set.of("待分配", "未开始", "待处理", "进行中");
    private static final Set<String> FALLBACK_GEO_TYPES = Set.of(
            "文章撰写",
            "文章发布",
            Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER,
            Constants.TASK_TYPE_GEO_ASSIGN_WRITER
    );

    private final SysTaskMapper taskMapper;
    private final SysTaskAssigneeMapper assigneeMapper;
    private final SysTaskTypeMapper taskTypeMapper;
    private final GeoContentPlacementMapper placementMapper;
    private final DataScopeFilter dataScopeFilter;

    @Override
    public BoardGeoTaskSummaryVO summary(BoardGeoTaskQueryDTO query) {
        QueryCtx ctx = resolveCtx(query);
        List<TaskView> views = loadViews(ctx);

        long due = 0, overdue = 0, done = 0, rangeCompleted = 0, rangeTotal = 0;
        for (TaskView v : views) {
            if (isDue(v, ctx)) {
                due++;
                if (v.overdue) {
                    overdue++;
                }
            }
            if (isDoneInRange(v, ctx)) {
                done++;
            }
            if (inCompletionScope(v, ctx)) {
                rangeTotal++;
                if (v.done) {
                    rangeCompleted++;
                }
            }
        }

        BoardGeoTaskSummaryVO vo = new BoardGeoTaskSummaryVO();
        vo.setGrain(ctx.grain);
        vo.setStartDate(ctx.rangeStart.format(DAY));
        vo.setEndDate(ctx.rangeEnd.format(DAY));
        vo.setDueCount(due);
        vo.setOverdueCount(overdue);
        vo.setDoneCount(done);
        vo.setRangeCompleted(rangeCompleted);
        vo.setRangeTotal(rangeTotal);
        vo.setCompletionRate(pct(rangeCompleted, rangeTotal));
        return vo;
    }

    @Override
    public List<BoardGeoTaskAggRowVO> aggregate(BoardGeoTaskAggQueryDTO query) {
        if (query == null || !StringUtils.hasText(query.getDim())) {
            return List.of();
        }
        String dim = normalizeDim(query.getDim());
        if (dim == null) {
            return List.of();
        }
        QueryCtx ctx = resolveCtx(query);
        List<TaskView> views = loadViews(ctx);

        Map<String, Agg> map = new LinkedHashMap<>();
        for (TaskView v : views) {
            // 完成率分母口径：计划截止在区间 或 区间内完成（与任务看板一致）
            if (!inCompletionScope(v, ctx)) {
                continue;
            }
            DimKey key = dimKeyOf(dim, v);
            Agg a = map.computeIfAbsent(key.dimKey, k -> new Agg(key));
            a.totalCount++;
            if (v.done) {
                a.doneCount++;
            }
            if (v.open) {
                a.openCount++;
            }
            if (isDue(v, ctx)) {
                a.dueCount++;
                if (v.overdue) {
                    a.overdueCount++;
                }
            }
        }

        return map.values().stream()
                .sorted(Comparator
                        .comparingDouble((Agg a) -> pct(a.doneCount, a.totalCount)).reversed()
                        .thenComparing(Comparator.comparingLong((Agg a) -> a.totalCount).reversed())
                        .thenComparing(a -> a.name, Comparator.nullsLast(String::compareTo)))
                .map(Agg::toVo)
                .toList();
    }

    @Override
    public PageResult<BoardGeoTaskRowVO> drill(BoardGeoTaskDrillQueryDTO query) {
        if (query == null || !StringUtils.hasText(query.getDim())) {
            return new PageResult<>(0L, List.of());
        }
        String dim = normalizeDim(query.getDim());
        if (dim == null) {
            return new PageResult<>(0L, List.of());
        }
        QueryCtx ctx = resolveCtx(query);
        String sub = StringUtils.hasText(query.getSubFilter()) ? query.getSubFilter().trim() : "all";
        List<TaskView> views = loadViews(ctx);

        List<TaskView> matched = new ArrayList<>();
        for (TaskView v : views) {
            if (!matchDim(dim, query, v)) {
                continue;
            }
            if (!inCompletionScope(v, ctx)) {
                continue;
            }
            if ("done".equalsIgnoreCase(sub) && !v.done) {
                continue;
            }
            if ("open".equalsIgnoreCase(sub) && !v.open) {
                continue;
            }
            matched.add(v);
        }

        matched.sort(Comparator
                .comparing((TaskView v) -> v.task.getPlanEndTime(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(v -> v.task.getId(), Comparator.reverseOrder()));

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        int from = Math.min((pageNum - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());

        Map<Long, String> assigneeNames = loadAssigneeNames(
                matched.subList(from, to).stream().map(v -> v.task.getId()).toList());

        List<BoardGeoTaskRowVO> rows = matched.subList(from, to).stream()
                .map(v -> toRow(v, assigneeNames.getOrDefault(v.task.getId(), "")))
                .toList();
        return new PageResult<>((long) matched.size(), rows);
    }

    private List<TaskView> loadViews(QueryCtx ctx) {
        Set<String> geoTypes = loadGeoTaskTypes();
        LambdaQueryWrapper<SysTask> w = new LambdaQueryWrapper<SysTask>()
                .ne(SysTask::getStatus, "已取消")
                .and(q -> q.eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                        .or()
                        .in(!geoTypes.isEmpty(), SysTask::getTaskType, geoTypes))
                .in(ctx.taskTypes != null && !ctx.taskTypes.isEmpty(), SysTask::getTaskType, ctx.taskTypes)
                .orderByDesc(SysTask::getId);
        dataScopeFilter.applyTask(w);
        List<SysTask> tasks = taskMapper.selectList(w);
        if (tasks.isEmpty()) {
            return List.of();
        }

        Set<Long> placementIds = tasks.stream()
                .filter(t -> Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT.equals(t.getBizType()) && t.getBizId() != null)
                .map(SysTask::getBizId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, GeoContentPlacement> placements = loadPlacements(placementIds);

        List<TaskView> views = new ArrayList<>();
        for (SysTask t : tasks) {
            GeoContentPlacement p = null;
            if (Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT.equals(t.getBizType()) && t.getBizId() != null) {
                p = placements.get(t.getBizId());
            }
            Long topicId = p != null && p.getTopicId() != null ? p.getTopicId() : 0L;
            String topicName = p != null && StringUtils.hasText(p.getTopicName()) ? p.getTopicName().trim() : "未关联话题";
            if (topicId == 0L && (p == null || !StringUtils.hasText(p.getTopicName()))) {
                topicName = "未关联话题";
            }
            Long writerId = p != null && p.getOwnerUserId() != null ? p.getOwnerUserId() : 0L;
            String writerName = p != null && StringUtils.hasText(p.getOwnerName())
                    ? p.getOwnerName().trim()
                    : (writerId == 0L ? "未分配撰写人" : ("用户" + writerId));
            String targetQuestion = p != null ? p.getTargetQuestion() : null;

            if (ctx.topicIds != null && !ctx.topicIds.isEmpty() && !ctx.topicIds.contains(topicId)) {
                continue;
            }
            if (ctx.filterUserIds != null && !ctx.filterUserIds.isEmpty() && !ctx.filterUserIds.contains(writerId)) {
                continue;
            }

            String st = nz(t.getStatus(), "未开始");
            boolean open = OPEN.contains(st);
            boolean done = "已完成".equals(st);
            LocalDate planDay = t.getPlanEndTime() == null ? null : t.getPlanEndTime().toLocalDate();
            LocalDate actualDay = resolveActualEndDay(t);
            boolean overdue = open && t.getPlanEndTime() != null && t.getPlanEndTime().isBefore(ctx.now);

            TaskView v = new TaskView();
            v.task = t;
            v.topicId = topicId;
            v.topicName = topicName;
            v.writerUserId = writerId;
            v.writerName = writerName;
            v.targetQuestion = targetQuestion;
            v.open = open;
            v.done = done;
            v.planDay = planDay;
            v.actualDay = actualDay;
            v.overdue = overdue;
            views.add(v);
        }
        return views;
    }

    private Set<String> loadGeoTaskTypes() {
        Set<String> types = new HashSet<>(FALLBACK_GEO_TYPES);
        List<SysTaskType> configured = taskTypeMapper.selectList(new LambdaQueryWrapper<SysTaskType>()
                .eq(SysTaskType::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                .eq(SysTaskType::getIsActive, 1));
        for (SysTaskType tt : configured) {
            if (StringUtils.hasText(tt.getTypeName())) {
                types.add(tt.getTypeName().trim());
            }
        }
        return types;
    }

    private Map<Long, GeoContentPlacement> loadPlacements(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<GeoContentPlacement> list = placementMapper.selectBatchIds(ids);
        Map<Long, GeoContentPlacement> map = new LinkedHashMap<>();
        for (GeoContentPlacement p : list) {
            if (p.getId() != null) {
                map.put(p.getId(), p);
            }
        }
        return map;
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

    private static boolean inCompletionScope(TaskView v, QueryCtx ctx) {
        if ("已取消".equals(nz(v.task.getStatus(), "未开始"))) {
            return false;
        }
        boolean planInRange = v.planDay != null && inRange(v.planDay, ctx.rangeStart, ctx.rangeEnd);
        boolean doneInRange = v.done && v.actualDay != null && inRange(v.actualDay, ctx.rangeStart, ctx.rangeEnd);
        return planInRange || doneInRange;
    }

    private static boolean isDue(TaskView v, QueryCtx ctx) {
        return v.open && v.planDay != null && inRange(v.planDay, ctx.rangeStart, ctx.rangeEnd);
    }

    private static boolean isDoneInRange(TaskView v, QueryCtx ctx) {
        return v.done && v.actualDay != null && inRange(v.actualDay, ctx.rangeStart, ctx.rangeEnd);
    }

    private static boolean inRange(LocalDate day, LocalDate start, LocalDate end) {
        return day != null && !day.isBefore(start) && !day.isAfter(end);
    }

    private static String normalizeDim(String dim) {
        if (!StringUtils.hasText(dim)) {
            return null;
        }
        String d = dim.trim();
        if ("topic".equals(d) || "writer".equals(d) || "taskType".equals(d)) {
            return d;
        }
        return null;
    }

    private static DimKey dimKeyOf(String dim, TaskView v) {
        return switch (dim) {
            case "writer" -> new DimKey(String.valueOf(v.writerUserId), v.writerName, null, v.writerUserId, null);
            case "taskType" -> {
                String type = StringUtils.hasText(v.task.getTaskType()) ? v.task.getTaskType().trim() : "未分类";
                yield new DimKey(type, type, null, null, type);
            }
            default -> new DimKey(String.valueOf(v.topicId), v.topicName, v.topicId, null, null);
        };
    }

    private static boolean matchDim(String dim, BoardGeoTaskDrillQueryDTO query, TaskView v) {
        return switch (dim) {
            case "writer" -> {
                long want = query.getWriterUserId() == null ? 0L : query.getWriterUserId();
                yield Objects.equals(v.writerUserId, want);
            }
            case "taskType" -> {
                String want = StringUtils.hasText(query.getTaskType()) ? query.getTaskType().trim() : "未分类";
                String actual = StringUtils.hasText(v.task.getTaskType()) ? v.task.getTaskType().trim() : "未分类";
                yield want.equals(actual);
            }
            default -> {
                long want = query.getTopicId() == null ? 0L : query.getTopicId();
                yield Objects.equals(v.topicId, want);
            }
        };
    }

    private BoardGeoTaskRowVO toRow(TaskView v, String assigneeNames) {
        SysTask t = v.task;
        BoardGeoTaskRowVO row = new BoardGeoTaskRowVO();
        row.setId(t.getId());
        row.setTitle(t.getTitle());
        row.setTaskType(t.getTaskType());
        row.setStatus(t.getStatus());
        row.setPriority(t.getPriority());
        row.setProgress(t.getProgress());
        row.setTopicName(v.topicName);
        row.setWriterName(v.writerName);
        row.setOwnerName(t.getOwnerName());
        row.setAssigneeNames(assigneeNames);
        row.setTargetQuestion(v.targetQuestion);
        row.setPlanEndTime(t.getPlanEndTime());
        row.setActualEndTime(t.getActualEndTime() != null ? t.getActualEndTime()
                : (v.done ? t.getUpdateTime() : null));
        row.setOverdue(v.overdue);
        if (v.overdue && t.getPlanEndTime() != null) {
            long days = ChronoUnit.DAYS.between(t.getPlanEndTime().toLocalDate(), LocalDate.now());
            row.setOverdueDays((int) Math.max(days, 0));
        }
        fillTiming(row, t, v.done);
        return row;
    }

    private void fillTiming(BoardGeoTaskRowVO row, SysTask t, boolean done) {
        if (!done) {
            return;
        }
        LocalDateTime actualAt = resolveActualEndAt(t);
        if (t.getPlanEndTime() == null) {
            row.setTimingTag(actualAt == null ? "UNKNOWN" : "ON_TIME");
            row.setTimingLabel(actualAt == null ? "无完成时间" : "正常完成");
            return;
        }
        if (actualAt == null) {
            row.setTimingTag("UNKNOWN");
            row.setTimingLabel("无完成时间");
            return;
        }
        long days = ChronoUnit.DAYS.between(t.getPlanEndTime().toLocalDate(), actualAt.toLocalDate());
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

    private QueryCtx resolveCtx(BoardGeoTaskQueryDTO query) {
        BoardGeoTaskQueryDTO q = query == null ? new BoardGeoTaskQueryDTO() : query;
        String grain = normalizeGrain(q.getGrain());
        LocalDate today = LocalDate.now();
        LocalDate[] defaults = defaultRange(grain, today);
        LocalDate rangeStart = parseDay(q.getStartDate(), defaults[0]);
        LocalDate rangeEnd = parseDay(q.getEndDate(), defaults[1]);
        if (rangeEnd.isBefore(rangeStart)) {
            LocalDate tmp = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = tmp;
        }
        List<Long> writers = q.getFilterUserIds() == null ? List.of() : q.getFilterUserIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        List<Long> topics = q.getTopicIds() == null ? List.of() : q.getTopicIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        List<String> types = q.getTaskTypes() == null ? List.of() : q.getTaskTypes().stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList();
        return new QueryCtx(grain, rangeStart, rangeEnd, today.atTime(LocalTime.now()), writers, topics, types);
    }

    private static LocalDate[] defaultRange(String grain, LocalDate asOf) {
        return switch (grain) {
            case "day" -> new LocalDate[]{asOf.minusMonths(1), asOf};
            case "month" -> new LocalDate[]{asOf.minusMonths(3).withDayOfMonth(1), asOf.withDayOfMonth(asOf.lengthOfMonth())};
            case "year" -> new LocalDate[]{asOf.minusYears(1).withDayOfYear(1), asOf.withDayOfYear(asOf.lengthOfYear())};
            default -> new LocalDate[]{asOf.minusWeeks(3).with(ISO.dayOfWeek(), 1), asOf.with(ISO.dayOfWeek(), 7)};
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

    private static LocalDate resolveActualEndDay(SysTask t) {
        LocalDateTime at = resolveActualEndAt(t);
        return at == null ? null : at.toLocalDate();
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

    private record QueryCtx(String grain, LocalDate rangeStart, LocalDate rangeEnd, LocalDateTime now,
                            List<Long> filterUserIds, List<Long> topicIds, List<String> taskTypes) {
    }

    private record DimKey(String dimKey, String name, Long topicId, Long writerUserId, String taskType) {
    }

    private static final class Agg {
        final String dimKey;
        final String name;
        final Long topicId;
        final Long writerUserId;
        final String taskType;
        long doneCount;
        long totalCount;
        long openCount;
        long dueCount;
        long overdueCount;

        Agg(DimKey key) {
            this.dimKey = key.dimKey;
            this.name = key.name;
            this.topicId = key.topicId;
            this.writerUserId = key.writerUserId;
            this.taskType = key.taskType;
        }

        BoardGeoTaskAggRowVO toVo() {
            BoardGeoTaskAggRowVO vo = new BoardGeoTaskAggRowVO();
            vo.setDimKey(dimKey);
            vo.setName(name);
            vo.setTopicId(topicId);
            vo.setWriterUserId(writerUserId);
            vo.setTaskType(taskType);
            vo.setDoneCount(doneCount);
            vo.setTotalCount(totalCount);
            vo.setCompletionRate(pct(doneCount, totalCount));
            vo.setOpenCount(openCount);
            vo.setDueCount(dueCount);
            vo.setOverdueCount(overdueCount);
            return vo;
        }
    }

    private static final class TaskView {
        SysTask task;
        Long topicId;
        String topicName;
        Long writerUserId;
        String writerName;
        String targetQuestion;
        boolean open;
        boolean done;
        LocalDate planDay;
        LocalDate actualDay;
        boolean overdue;
    }
}
