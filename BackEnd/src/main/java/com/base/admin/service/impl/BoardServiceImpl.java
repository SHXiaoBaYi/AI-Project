package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.BoardTaskDrillQueryDTO;
import com.base.admin.domain.dto.BoardTaskSummaryQueryDTO;
import com.base.admin.domain.dto.SysTaskQueryDTO;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskAssignee;
import com.base.admin.domain.vo.BoardChartPointVO;
import com.base.admin.domain.vo.BoardTaskSummaryVO;
import com.base.admin.domain.vo.SysTaskVO;
import com.base.admin.mapper.SysTaskAssigneeMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.service.BoardService;
import com.base.admin.service.SysTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Map<Integer, String> PRIORITY_LABEL = Map.of(
            1, "低", 2, "中", 3, "高", 4, "紧急");

    private final SysTaskMapper taskMapper;
    private final SysTaskAssigneeMapper assigneeMapper;
    private final SysTaskService taskService;

    @Override
    public BoardTaskSummaryVO taskSummary(BoardTaskSummaryQueryDTO query) {
        List<SysTask> tasks = loadTasks(query);
        LocalDateTime now = LocalDateTime.now();
        BoardTaskSummaryVO vo = new BoardTaskSummaryVO();
        vo.setTotal(tasks.size());
        long pending = 0, doing = 0, done = 0, cancelled = 0, overdue = 0;
        Map<String, Long> statusCnt = new LinkedHashMap<>();
        Map<String, Long> priorityCnt = new LinkedHashMap<>();
        Map<String, Long> ownerOpen = new LinkedHashMap<>();
        Map<String, long[]> daily = new LinkedHashMap<>();

        for (SysTask t : tasks) {
            String st = nz(t.getStatus(), "未开始");
            statusCnt.merge(st, 1L, Long::sum);
            String pr = PRIORITY_LABEL.getOrDefault(t.getPriority() == null ? 2 : t.getPriority(), "中");
            priorityCnt.merge(pr, 1L, Long::sum);

            switch (st) {
                case "待分配", "未开始", "待处理" -> pending++;
                case "进行中" -> doing++;
                case "已完成" -> done++;
                case "已取消" -> cancelled++;
                default -> {
                }
            }
            if (isOverdue(t, now)) {
                overdue++;
            }
            if (!"已完成".equals(st) && !"已取消".equals(st)) {
                String owner = StringUtils.hasText(t.getOwnerName()) ? t.getOwnerName() : ("用户" + t.getOwnerUserId());
                ownerOpen.merge(owner, 1L, Long::sum);
            }
            if (t.getCreateTime() != null) {
                String day = t.getCreateTime().toLocalDate().format(DAY);
                daily.computeIfAbsent(day, k -> new long[2])[0]++;
            }
            if ("已完成".equals(st) && t.getActualEndTime() != null) {
                String day = t.getActualEndTime().toLocalDate().format(DAY);
                daily.computeIfAbsent(day, k -> new long[2])[1]++;
            } else if ("已完成".equals(st) && t.getUpdateTime() != null) {
                String day = t.getUpdateTime().toLocalDate().format(DAY);
                daily.computeIfAbsent(day, k -> new long[2])[1]++;
            }
        }

        vo.setPending(pending);
        vo.setDoing(doing);
        vo.setDone(done);
        vo.setCancelled(cancelled);
        vo.setOverdue(overdue);
        long base = done + pending + doing;
        vo.setCompletionRate(base == 0 ? 0 : Math.round(done * 10000.0 / base) / 100.0);

        vo.setStatusChart(toPoints(statusCnt, "状态"));
        vo.setPriorityChart(toPoints(priorityCnt, "优先级"));
        vo.setOwnerLoadChart(ownerOpen.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(12)
                .map(e -> new BoardChartPointVO(e.getKey(), e.getValue(), "未完成"))
                .toList());

        List<BoardChartPointVO> trend = new ArrayList<>();
        daily.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            trend.add(new BoardChartPointVO(e.getKey(), e.getValue()[0], "新建"));
            trend.add(new BoardChartPointVO(e.getKey(), e.getValue()[1], "完成"));
        });
        vo.setDailyTrendChart(trend);
        return vo;
    }

    @Override
    public PageResult<SysTaskVO> taskDrill(BoardTaskDrillQueryDTO query) {
        SysTaskQueryDTO q = new SysTaskQueryDTO();
        q.setPageNum(query.getPageNum());
        q.setPageSize(query.getPageSize());
        q.setTaskType(query.getTaskType());
        q.setOwnerUserId(query.getOwnerUserId());
        q.setAssigneeUserId(query.getAssigneeUserId());

        String metric = query.getMetric() == null ? "" : query.getMetric().trim();
        String dimKey = query.getDimKey();
        switch (metric) {
            case "pending" -> {
                // 待分配+待处理：列表侧二次过滤，此处不锁单一 status
            }
            case "doing" -> q.setStatus("进行中");
            case "done" -> q.setStatus("已完成");
            case "cancelled" -> q.setStatus("已取消");
            case "overdue" -> q.setOverdueOnly(true);
            case "status" -> {
                if (StringUtils.hasText(dimKey)) {
                    q.setStatus(dimKey);
                }
            }
            case "priority" -> {
                if (StringUtils.hasText(dimKey)) {
                    PRIORITY_LABEL.entrySet().stream()
                            .filter(e -> e.getValue().equals(dimKey) || String.valueOf(e.getKey()).equals(dimKey))
                            .findFirst()
                            .ifPresent(e -> q.setPriority(e.getKey()));
                }
            }
            case "owner" -> {
                // dimKey 可能是姓名，列表侧再滤；此处仅按负责人筛选若可解析为 Long
                if (StringUtils.hasText(dimKey) && dimKey.chars().allMatch(Character::isDigit)) {
                    q.setOwnerUserId(Long.parseLong(dimKey));
                }
            }
            default -> {
            }
        }

        PageResult<SysTaskVO> page = taskService.list(q);
        // 时间窗与负责人姓名二次过滤
        LocalDateTime start = parseStart(query.getStartDate());
        LocalDateTime end = parseEnd(query.getEndDate());
        List<SysTaskVO> filtered = page.getRows().stream()
                .filter(t -> inCreateRange(t, start, end))
                .filter(t -> {
                    if (!"owner".equals(metric) || !StringUtils.hasText(dimKey) || dimKey.chars().allMatch(Character::isDigit)) {
                        return true;
                    }
                    return dimKey.equals(t.getOwnerName());
                })
                .filter(t -> {
                    if (!"pending".equals(metric)) {
                        return true;
                    }
                    return "未开始".equals(t.getStatus()) || "待处理".equals(t.getStatus()) || "待分配".equals(t.getStatus());
                })
                .toList();

        if ("pending".equals(metric) || ("owner".equals(metric) && StringUtils.hasText(dimKey) && !dimKey.chars().allMatch(Character::isDigit))
                || start != null || end != null) {
            // 简化：二次过滤后不分页准确总数，返回当前页过滤结果
            return new PageResult<>((long) filtered.size(), filtered);
        }
        return page;
    }

    private List<SysTask> loadTasks(BoardTaskSummaryQueryDTO query) {
        LocalDateTime start = parseStart(query.getStartDate());
        LocalDateTime end = parseEnd(query.getEndDate());

        Set<Long> byAssignee = null;
        if (query.getAssigneeUserId() != null) {
            byAssignee = assigneeMapper.selectList(new LambdaQueryWrapper<SysTaskAssignee>()
                            .eq(SysTaskAssignee::getUserId, query.getAssigneeUserId())
                            .select(SysTaskAssignee::getTaskId))
                    .stream().map(SysTaskAssignee::getTaskId).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (byAssignee.isEmpty()) {
                return List.of();
            }
        }

        LambdaQueryWrapper<SysTask> w = new LambdaQueryWrapper<SysTask>()
                .eq(query.getOwnerUserId() != null, SysTask::getOwnerUserId, query.getOwnerUserId())
                .eq(StringUtils.hasText(query.getTaskType()), SysTask::getTaskType, query.getTaskType())
                .in(byAssignee != null, SysTask::getId, byAssignee)
                .ge(start != null, SysTask::getCreateTime, start)
                .le(end != null, SysTask::getCreateTime, end)
                .orderByDesc(SysTask::getId);
        return taskMapper.selectList(w);
    }

    private static List<BoardChartPointVO> toPoints(Map<String, Long> map, String series) {
        return map.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> new BoardChartPointVO(e.getKey(), e.getValue(), series))
                .toList();
    }

    private static boolean isOverdue(SysTask t, LocalDateTime now) {
        if (t.getPlanEndTime() == null) {
            return false;
        }
        if ("已完成".equals(t.getStatus()) || "已取消".equals(t.getStatus())) {
            return false;
        }
        return t.getPlanEndTime().isBefore(now);
    }

    private static boolean inCreateRange(SysTaskVO t, LocalDateTime start, LocalDateTime end) {
        if (t.getCreateTime() == null) {
            return start == null && end == null;
        }
        if (start != null && t.getCreateTime().isBefore(start)) {
            return false;
        }
        if (end != null && t.getCreateTime().isAfter(end)) {
            return false;
        }
        return true;
    }

    private static LocalDateTime parseStart(String day) {
        if (!StringUtils.hasText(day)) {
            return null;
        }
        return LocalDate.parse(day.trim(), DAY).atStartOfDay();
    }

    private static LocalDateTime parseEnd(String day) {
        if (!StringUtils.hasText(day)) {
            return null;
        }
        return LocalDate.parse(day.trim(), DAY).atTime(LocalTime.MAX);
    }

    private static String nz(String s, String d) {
        return StringUtils.hasText(s) ? s : d;
    }
}
