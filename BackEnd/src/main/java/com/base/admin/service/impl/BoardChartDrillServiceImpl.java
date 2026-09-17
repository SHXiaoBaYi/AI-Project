package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.BoardChartDrillQueryDTO;
import com.base.admin.domain.dto.BoardChartStackItemDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskAssignee;
import com.base.admin.domain.entity.SysTaskType;
import com.base.admin.domain.vo.BoardChartBarVO;
import com.base.admin.domain.vo.BoardChartDrillVO;
import com.base.admin.domain.vo.BoardChartStackItemVO;
import com.base.admin.domain.vo.BoardChartTrendPointVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.GeoMonitorDailyMapper;
import com.base.admin.mapper.GeoTopicMapper;
import com.base.admin.mapper.SysTaskAssigneeMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.service.BoardChartDrillService;
import com.base.admin.service.SysTaskTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardChartDrillServiceImpl implements BoardChartDrillService {

    private final GeoMonitorDailyMapper dailyMapper;
    private final GeoTopicMapper topicMapper;
    private final GeoContentPlacementMapper placementMapper;
    private final SysTaskMapper taskMapper;
    private final SysTaskAssigneeMapper assigneeMapper;
    private final SysTaskTypeService taskTypeService;

    @Override
    public BoardChartDrillVO drill(BoardChartDrillQueryDTO query) {
        List<BoardChartStackItemDTO> stack = new ArrayList<>(
                query.getStack() == null ? List.of() : query.getStack());
        String domain = nz(query.getDomain(), "geo").toLowerCase();
        String dim = nz(query.getDim(), "topic").toLowerCase();
        if ("task".equals(domain) && "topic".equals(dim)) {
            dim = "theme";
        }
        if ("geo".equals(domain)) {
            // GEO 仅话题维
            dim = "topic";
        }
        if (StringUtils.hasText(query.getClickKey())) {
            List<String> path = drillPath(domain, dim);
            int depth = stack.size();
            if (depth >= path.size() - 1) {
                throw new BusinessException("当前层级不可再下钻");
            }
            String field = path.get(depth);
            BoardChartStackItemDTO node = new BoardChartStackItemDTO();
            node.setField(field);
            node.setKey(query.getClickKey().trim());
            node.setLabel(shortLabel(query.getClickKey().trim()));
            stack.add(node);
        }

        String grain = nz(query.getGrain(), "week").toLowerCase();
        String personRole = nz(query.getPersonRole(), "writer").toLowerCase();
        LocalDate end = parseDate(query.getEndDate(), LocalDate.now());
        LocalDate start = parseDate(query.getStartDate(), defaultStart(end, grain));
        if (start.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }

        if ("geo".equals(domain)) {
            return buildGeo(dim, personRole, grain, start, end, stack, nz(query.getMetric(), "mentionRate"));
        }
        if ("task".equals(domain)) {
            return buildTask(dim, grain, start, end, stack, nz(query.getMetric(), "doneRate"));
        }
        throw new BusinessException("不支持的业务域: " + domain);
    }

    private BoardChartDrillVO buildGeo(String dim, String personRole, String grain,
                                       LocalDate start, LocalDate end,
                                       List<BoardChartStackItemDTO> stack, String metric) {
        String axis = currentAxisFixed("geo", dim, stack);
        boolean canDrillMore = stack.size() < drillPath("geo", dim).size() - 1;

        Map<String, Agg> cur = aggregateGeo(axis, dim, personRole, start, end, stack);
        long days = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
        Map<String, Agg> momMap = aggregateGeo(axis, dim, personRole, start.minusDays(days), end.minusDays(days), stack);
        Map<String, Agg> yoyMap = aggregateGeo(axis, dim, personRole, start.minusYears(1), end.minusYears(1), stack);

        BoardChartDrillVO vo = baseVo("geo", axis, metric, metricLabel(metric), grain, start, end, stack);
        vo.setBars(toBars(cur, momMap, yoyMap, canDrillMore, true));
        vo.setTrend(buildGeoTrend(axis, dim, personRole, grain, start, end, stack, true, canDrillMore));
        vo.setChartDrillable(canDrillMore && vo.getBars().stream().anyMatch(BoardChartBarVO::isDrillable));
        fillKpi(vo, cur, momMap, yoyMap, true);
        vo.setTitle(geoTitle(dim, personRole, axis, stack));
        return vo;
    }

    private BoardChartDrillVO buildTask(String dim, String grain, LocalDate start, LocalDate end,
                                        List<BoardChartStackItemDTO> stack, String metric) {
        String axis = currentAxisFixed("task", dim, stack);
        boolean canDrillMore = stack.size() < drillPath("task", dim).size() - 1;

        List<BizTask> bizTasks = loadBizTasks();
        Map<String, Agg> cur = aggregateTask(axis, dim, bizTasks, start, end, stack);
        long days = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
        Map<String, Agg> momMap = aggregateTask(axis, dim, bizTasks, start.minusDays(days), end.minusDays(days), stack);
        Map<String, Agg> yoyMap = aggregateTask(axis, dim, bizTasks, start.minusYears(1), end.minusYears(1), stack);

        boolean rateMetric = "doneRate".equals(metric) || "completionRate".equals(metric);
        BoardChartDrillVO vo = baseVo("task", axis, metric, rateMetric ? "完成率%" : "任务数", grain, start, end, stack);
        vo.setBars(toBars(cur, momMap, yoyMap, canDrillMore, rateMetric));
        vo.setTrend(buildTaskTrend(axis, dim, bizTasks, grain, start, end, stack, rateMetric, canDrillMore));
        vo.setChartDrillable(canDrillMore && vo.getBars().stream().anyMatch(BoardChartBarVO::isDrillable));
        fillKpi(vo, cur, momMap, yoyMap, rateMetric);
        vo.setTitle(taskTitle(dim, axis, stack));
        return vo;
    }

    private Map<String, Agg> aggregateGeo(String axis, String primaryDim, String personRole,
                                          LocalDate start, LocalDate end,
                                          List<BoardChartStackItemDTO> stack) {
        List<GeoMonitorDaily> dailies = dailyMapper.selectList(new LambdaQueryWrapper<GeoMonitorDaily>()
                .ge(GeoMonitorDaily::getInspectDate, start)
                .le(GeoMonitorDaily::getInspectDate, end));
        Map<Long, String> topicNames = loadTopicNames();
        Map<Long, PersonRef> topicPerson = buildTopicPersonIndex(personRole);

        Map<String, Agg> map = new LinkedHashMap<>();
        for (GeoMonitorDaily d : dailies) {
            String topicKey = topicNameKey(d, topicNames);
            String questionKey = questionKey(d);
            PersonRef person = resolveGeoPerson(d, personRole, topicPerson);
            String personKey = person == null ? "未分配" : person.label();
            String platform = StringUtils.hasText(d.getPlatform()) ? d.getPlatform().trim() : "未知平台";

            if (!matchStackGeo(stack, topicKey, questionKey, personKey, platform)) {
                continue;
            }
            String bucket = switch (axis) {
                case "topic" -> topicKey;
                case "question" -> questionKey;
                case "person" -> personKey;
                case "platform" -> platform;
                default -> topicKey;
            };
            Agg agg = map.computeIfAbsent(bucket, k -> new Agg());
            agg.sample++;
            if (d.getMentioned() != null && d.getMentioned() == 1) {
                agg.hit++;
            }
        }
        return map;
    }

    private Map<String, Agg> aggregateTask(String axis, String primaryDim, List<BizTask> all,
                                           LocalDate start, LocalDate end,
                                           List<BoardChartStackItemDTO> stack) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.atTime(LocalTime.MAX);
        Map<String, Agg> map = new LinkedHashMap<>();
        for (BizTask t : all) {
            if (t.anchorTime == null || t.anchorTime.isBefore(from) || t.anchorTime.isAfter(to)) {
                continue;
            }
            if (!matchStackTask(stack, t)) {
                continue;
            }
            String bucket = switch (axis) {
                case "theme" -> t.themeKey;
                case "person" -> t.personLabel;
                case "stage" -> t.stage;
                default -> t.themeKey;
            };
            Agg agg = map.computeIfAbsent(bucket, k -> new Agg());
            agg.sample++;
            if ("已完成".equals(t.stage)) {
                agg.hit++;
            }
        }
        return map;
    }

    private List<BizTask> loadBizTasks() {
        List<SysTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .ne(SysTask::getStatus, "已取消"));
        Map<Long, List<SysTaskAssignee>> assignees = loadAssignees(tasks.stream().map(SysTask::getId).toList());
        Map<String, SysTaskType> types = taskTypeService.listOptions().stream()
                .collect(Collectors.toMap(SysTaskType::getTypeName, t -> t, (a, b) -> a));

        Set<String> spawnSources = types.values().stream()
                .filter(t -> StringUtils.hasText(t.getSpawnTaskType()))
                .map(SysTaskType::getTypeName)
                .collect(Collectors.toSet());
        Set<String> spawnTargets = types.values().stream()
                .map(SysTaskType::getSpawnTaskType)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        Map<String, List<SysTask>> flowGroups = new LinkedHashMap<>();
        List<SysTask> singles = new ArrayList<>();
        for (SysTask t : tasks) {
            boolean inFlow = (StringUtils.hasText(t.getBizType()) && t.getBizId() != null)
                    && (spawnSources.contains(nz(t.getTaskType(), "")) || spawnTargets.contains(nz(t.getTaskType(), "")));
            if (inFlow) {
                String flow = resolveFlowCode(t, types);
                String gk = t.getBizType() + "|" + t.getBizId() + "|" + flow;
                flowGroups.computeIfAbsent(gk, k -> new ArrayList<>()).add(t);
            } else {
                singles.add(t);
            }
        }

        List<BizTask> result = new ArrayList<>();
        for (List<SysTask> group : flowGroups.values()) {
            result.add(mergeFlow(group, assignees, types));
        }
        for (SysTask t : singles) {
            result.add(fromSingle(t, assignees.getOrDefault(t.getId(), List.of())));
        }
        return result;
    }

    private BizTask mergeFlow(List<SysTask> group, Map<Long, List<SysTaskAssignee>> assignees,
                              Map<String, SysTaskType> types) {
        SysTask exec = group.stream()
                .filter(t -> {
                    SysTaskType cfg = types.get(t.getTaskType());
                    return cfg == null || !StringUtils.hasText(cfg.getSpawnTaskType());
                })
                .findFirst()
                .orElse(group.getFirst());
        SysTask assign = group.stream()
                .filter(t -> {
                    SysTaskType cfg = types.get(t.getTaskType());
                    return cfg != null && StringUtils.hasText(cfg.getSpawnTaskType());
                })
                .findFirst()
                .orElse(group.getFirst());

        String stage;
        if (group.stream().anyMatch(t -> "已完成".equals(t.getStatus())
                && (types.get(t.getTaskType()) == null || !StringUtils.hasText(types.get(t.getTaskType()).getSpawnTaskType())))) {
            stage = "已完成";
        } else if (group.stream().anyMatch(t -> "进行中".equals(t.getStatus()))) {
            stage = "进行中";
        } else if (group.stream().anyMatch(t -> "未开始".equals(t.getStatus()) || "待处理".equals(t.getStatus()))) {
            stage = "未开始";
        } else if (group.stream().anyMatch(t -> "待分配".equals(t.getStatus()))) {
            stage = "待分配";
        } else {
            stage = nz(exec.getStatus(), "未开始");
        }

        SysTask personTask = "待分配".equals(stage) ? assign : exec;
        List<SysTaskAssignee> asg = assignees.getOrDefault(personTask.getId(), List.of());
        String personLabel = asg.isEmpty()
                ? (StringUtils.hasText(personTask.getOwnerName()) ? personTask.getOwnerName() : "未分配")
                : asg.getFirst().getUserName();

        String title = StringUtils.hasText(assign.getBizTitle()) ? assign.getBizTitle().trim()
                : (StringUtils.hasText(exec.getBizTitle()) ? exec.getBizTitle().trim() : stripTypePrefix(exec.getTitle()));
        String themeKey = assign.getBizType() + "#" + assign.getBizId() + "｜" + title;

        LocalDateTime anchor = assign.getCreateTime() != null ? assign.getCreateTime() : exec.getCreateTime();
        BizTask bt = new BizTask();
        bt.themeKey = themeKey;
        bt.themeLabel = title;
        bt.personLabel = personLabel;
        bt.stage = stage;
        bt.anchorTime = anchor;
        bt.bizType = assign.getBizType();
        bt.bizId = assign.getBizId();
        return bt;
    }

    private BizTask fromSingle(SysTask t, List<SysTaskAssignee> asg) {
        BizTask bt = new BizTask();
        if (StringUtils.hasText(t.getBizType()) && t.getBizId() != null) {
            String title = StringUtils.hasText(t.getBizTitle()) ? t.getBizTitle().trim() : stripTypePrefix(t.getTitle());
            bt.themeKey = t.getBizType() + "#" + t.getBizId() + "｜" + title;
            bt.themeLabel = title;
        } else {
            String title = stripTypePrefix(t.getTitle());
            bt.themeKey = "manual｜" + title + "#" + t.getId();
            bt.themeLabel = title;
        }
        bt.personLabel = asg.isEmpty()
                ? (StringUtils.hasText(t.getOwnerName()) ? t.getOwnerName() : "未分配")
                : asg.getFirst().getUserName();
        bt.stage = nz(t.getStatus(), "未开始");
        bt.anchorTime = t.getCreateTime();
        bt.bizType = t.getBizType();
        bt.bizId = t.getBizId();
        return bt;
    }

    private String resolveFlowCode(SysTask t, Map<String, SysTaskType> types) {
        SysTaskType cfg = types.get(t.getTaskType());
        if (cfg != null && Constants.TASK_ASSIGN_FIELD_WRITER.equals(nz(cfg.getAssignField(), ""))) {
            return "writer";
        }
        if (cfg != null && Constants.TASK_ASSIGN_FIELD_PUBLISHER.equals(nz(cfg.getAssignField(), ""))) {
            return "publisher";
        }
        if ("文章撰写".equals(t.getTaskType()) || Constants.TASK_TYPE_GEO_ASSIGN_WRITER.equals(t.getTaskType())) {
            return "writer";
        }
        if ("文章发布".equals(t.getTaskType()) || Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER.equals(t.getTaskType())) {
            return "publisher";
        }
        return "flow";
    }

    private static String stripTypePrefix(String title) {
        if (!StringUtils.hasText(title)) {
            return "未命名";
        }
        String t = title.trim();
        if (t.startsWith("【") && t.contains("】")) {
            return t.substring(t.indexOf('】') + 1).trim();
        }
        return t;
    }

    private boolean matchStackGeo(List<BoardChartStackItemDTO> stack, String topic, String question,
                                  String person, String platform) {
        for (BoardChartStackItemDTO s : stack) {
            if (s == null || !StringUtils.hasText(s.getKey())) {
                continue;
            }
            String f = nz(s.getField(), "");
            String k = s.getKey().trim();
            if ("topic".equals(f) && !k.equals(topic)) {
                return false;
            }
            if ("question".equals(f) && !k.equals(question)) {
                return false;
            }
            if ("person".equals(f) && !k.equals(person)) {
                return false;
            }
            if ("platform".equals(f) && !k.equals(platform)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchStackTask(List<BoardChartStackItemDTO> stack, BizTask t) {
        for (BoardChartStackItemDTO s : stack) {
            if (s == null || !StringUtils.hasText(s.getKey())) {
                continue;
            }
            String f = nz(s.getField(), "");
            String k = s.getKey().trim();
            if ("theme".equals(f) && !k.equals(t.themeKey)) {
                return false;
            }
            if ("person".equals(f) && !k.equals(t.personLabel)) {
                return false;
            }
            if ("stage".equals(f) && !k.equals(t.stage)) {
                return false;
            }
        }
        return true;
    }

    private List<String> drillPath(String domain, String primaryDim) {
        if ("geo".equals(domain)) {
            // GEO：话题 → 目标问题 → 平台（无人维）
            return List.of("topic", "question", "platform");
        }
        if ("person".equals(primaryDim)) {
            return List.of("person", "theme", "stage");
        }
        return List.of("theme", "person", "stage");
    }

    private String currentAxisFixed(String domain, String primaryDim, List<BoardChartStackItemDTO> stack) {
        List<String> path = drillPath(domain, primaryDim);
        int depth = stack == null ? 0 : stack.size();
        if (depth >= path.size()) {
            return path.getLast();
        }
        return path.get(depth);
    }

    private BoardChartDrillVO baseVo(String domain, String axis, String metric, String metricLabel,
                                     String grain, LocalDate start, LocalDate end,
                                     List<BoardChartStackItemDTO> stack) {
        BoardChartDrillVO vo = new BoardChartDrillVO();
        vo.setDomain(domain);
        vo.setAxisField(axis);
        vo.setMetric(metric);
        vo.setMetricLabel(metricLabel);
        vo.setGrain(grain);
        vo.setStartDate(start.toString());
        vo.setEndDate(end.toString());
        List<BoardChartStackItemVO> crumbs = new ArrayList<>();
        crumbs.add(new BoardChartStackItemVO("root", "root", "大盘"));
        if (stack != null) {
            for (BoardChartStackItemDTO s : stack) {
                String raw = shortLabel(StringUtils.hasText(s.getKey()) ? s.getKey() : s.getLabel());
                String fieldTag = switch (nz(s.getField(), "")) {
                    case "topic" -> "话题";
                    case "question" -> "目标问题";
                    case "person" -> "人";
                    case "platform" -> "平台";
                    case "theme" -> "主题";
                    case "stage" -> "状态";
                    default -> null;
                };
                String label = fieldTag == null ? raw : fieldTag + "：" + raw;
                crumbs.add(new BoardChartStackItemVO(s.getField(), s.getKey(), label));
            }
        }
        vo.setBreadcrumb(crumbs);
        return vo;
    }

    private List<BoardChartTrendPointVO> buildGeoTrend(String axis, String primaryDim, String personRole,
                                                       String grain, LocalDate start, LocalDate end,
                                                       List<BoardChartStackItemDTO> stack, boolean rateMetric,
                                                       boolean canDrillMore) {
        List<GeoMonitorDaily> dailies = dailyMapper.selectList(new LambdaQueryWrapper<GeoMonitorDaily>()
                .ge(GeoMonitorDaily::getInspectDate, start)
                .le(GeoMonitorDaily::getInspectDate, end));
        Map<Long, String> topicNames = loadTopicNames();
        Map<Long, PersonRef> topicPerson = buildTopicPersonIndex(personRole);
        Map<String, Agg> cells = new LinkedHashMap<>();
        for (GeoMonitorDaily d : dailies) {
            String topicKey = topicNameKey(d, topicNames);
            String questionKey = questionKey(d);
            PersonRef person = resolveGeoPerson(d, personRole, topicPerson);
            String personKey = person == null ? "未分配" : person.label();
            String platform = StringUtils.hasText(d.getPlatform()) ? d.getPlatform().trim() : "未知平台";
            if (!matchStackGeo(stack, topicKey, questionKey, personKey, platform)) {
                continue;
            }
            String series = switch (axis) {
                case "topic" -> topicKey;
                case "question" -> questionKey;
                case "person" -> personKey;
                case "platform" -> platform;
                default -> topicKey;
            };
            String time = timeBucket(d.getInspectDate(), grain);
            String cellKey = time + "\0" + series;
            Agg agg = cells.computeIfAbsent(cellKey, k -> new Agg());
            agg.sample++;
            if (d.getMentioned() != null && d.getMentioned() == 1) {
                agg.hit++;
            }
        }
        return toTrendPoints(cells, rateMetric, canDrillMore);
    }

    private List<BoardChartTrendPointVO> buildTaskTrend(String axis, String primaryDim, List<BizTask> all,
                                                        String grain, LocalDate start, LocalDate end,
                                                        List<BoardChartStackItemDTO> stack, boolean rateMetric,
                                                        boolean canDrillMore) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.atTime(LocalTime.MAX);
        Map<String, Agg> cells = new LinkedHashMap<>();
        for (BizTask t : all) {
            if (t.anchorTime == null || t.anchorTime.isBefore(from) || t.anchorTime.isAfter(to)) {
                continue;
            }
            if (!matchStackTask(stack, t)) {
                continue;
            }
            String series = switch (axis) {
                case "person" -> t.personLabel;
                case "stage" -> t.stage;
                default -> t.themeKey;
            };
            String time = timeBucket(t.anchorTime.toLocalDate(), grain);
            String cellKey = time + "\0" + series;
            Agg agg = cells.computeIfAbsent(cellKey, k -> new Agg());
            agg.sample++;
            if ("已完成".equals(t.stage)) {
                agg.hit++;
            }
        }
        return toTrendPoints(cells, rateMetric, canDrillMore);
    }

    private List<BoardChartTrendPointVO> toTrendPoints(Map<String, Agg> cells, boolean rateMetric, boolean canDrillMore) {
        Map<String, Long> seriesWeight = new HashMap<>();
        for (Map.Entry<String, Agg> e : cells.entrySet()) {
            String series = e.getKey().substring(e.getKey().indexOf('\0') + 1);
            seriesWeight.merge(series, e.getValue().sample, Long::sum);
        }
        Set<String> topSeries = seriesWeight.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        List<BoardChartTrendPointVO> points = new ArrayList<>();
        cells.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String[] parts = e.getKey().split("\0", 2);
                    if (parts.length < 2 || !topSeries.contains(parts[1])) {
                        return;
                    }
                    String seriesKey = parts[1];
                    BoardChartTrendPointVO p = new BoardChartTrendPointVO();
                    p.setAxis(parts[0]);
                    p.setSeries(shortLabel(seriesKey));
                    p.setSeriesKey(seriesKey);
                    p.setValue(round2(valueOf(e.getValue(), rateMetric)));
                    p.setSampleCount(e.getValue().sample);
                    p.setDrillable(canDrillMore && e.getValue().sample > 0);
                    points.add(p);
                });
        return points;
    }

    private static String timeBucket(LocalDate date, String grain) {
        if (date == null) {
            return "-";
        }
        return switch (grain) {
            case "month" -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            case "year" -> String.valueOf(date.getYear());
            case "week" -> {
                WeekFields wf = WeekFields.of(Locale.CHINA);
                int w = date.get(wf.weekOfWeekBasedYear());
                int y = date.get(wf.weekBasedYear());
                yield y + "-W" + String.format("%02d", w);
            }
            default -> date.toString();
        };
    }

    private List<BoardChartBarVO> toBars(Map<String, Agg> cur, Map<String, Agg> mom, Map<String, Agg> yoy,
                                         boolean layerDrillable, boolean rateMetric) {
        List<BoardChartBarVO> bars = new ArrayList<>();
        cur.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, Agg> e) -> valueOf(e.getValue(), rateMetric)).reversed())
                .limit(40)
                .forEach(e -> {
                    Agg a = e.getValue();
                    double v = valueOf(a, rateMetric);
                    Double momV = delta(v, valueOf(mom.get(e.getKey()), rateMetric), rateMetric);
                    Double yoyV = delta(v, valueOf(yoy.get(e.getKey()), rateMetric), rateMetric);
                    BoardChartBarVO bar = new BoardChartBarVO();
                    bar.setKey(e.getKey());
                    bar.setLabel(shortLabel(e.getKey()));
                    bar.setValue(round2(v));
                    bar.setMom(momV);
                    bar.setYoy(yoyV);
                    bar.setSampleCount(a == null ? 0 : a.sample);
                    // 末级或样本过少不可点；单桶且已是最细也可点由 layer 控制
                    bar.setDrillable(layerDrillable && a != null && a.sample > 0);
                    bars.add(bar);
                });
        return bars;
    }

    private void fillKpi(BoardChartDrillVO vo, Map<String, Agg> cur, Map<String, Agg> mom, Map<String, Agg> yoy,
                         boolean rateMetric) {
        Agg c = sum(cur);
        Agg m = sum(mom);
        Agg y = sum(yoy);
        double cv = valueOf(c, rateMetric);
        vo.setCurrentValue(round2(cv));
        vo.setMom(delta(cv, valueOf(m, rateMetric), rateMetric));
        vo.setYoy(delta(cv, valueOf(y, rateMetric), rateMetric));
    }

    private static Agg sum(Map<String, Agg> map) {
        Agg a = new Agg();
        if (map == null) {
            return a;
        }
        for (Agg x : map.values()) {
            a.sample += x.sample;
            a.hit += x.hit;
        }
        return a;
    }

    private static double valueOf(Agg a, boolean rate) {
        if (a == null || a.sample <= 0) {
            return 0;
        }
        if (rate) {
            return a.hit * 100.0 / a.sample;
        }
        return a.sample;
    }

    private static Double delta(double cur, double base, boolean rate) {
        if (base == 0 && cur == 0) {
            return 0d;
        }
        return round2(cur - base);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String shortLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return "-";
        }
        // 兼容旧「话题｜目标问题」合成 key
        if (key.contains("｜")) {
            String[] p = key.split("｜", 2);
            String left = p[0];
            String right = p.length > 1 ? p[1] : "";
            if (left.length() > 8) {
                left = left.substring(0, 7) + "…";
            }
            if (right.length() > 14) {
                right = right.substring(0, 13) + "…";
            }
            return left + "｜" + right;
        }
        return key.length() > 24 ? key.substring(0, 23) + "…" : key;
    }

    private String geoTitle(String dim, String role, String axis, List<BoardChartStackItemDTO> stack) {
        String axisLabel = switch (axis) {
            case "topic" -> "话题";
            case "question" -> "目标问题";
            case "platform" -> "平台";
            default -> "话题";
        };
        return "GEO · 话题维 · 按" + axisLabel + (stack == null || stack.isEmpty() ? "" : "（已下钻）");
    }

    private String taskTitle(String dim, String axis, List<BoardChartStackItemDTO> stack) {
        String axisLabel = switch (axis) {
            case "person" -> "人";
            case "stage" -> "阶段";
            default -> "主题";
        };
        String base = "person".equals(dim) ? "任务 · 人维" : "任务 · 主题维";
        return base + " · 按" + axisLabel + (stack == null || stack.isEmpty() ? "" : "（已下钻）");
    }

    private String metricLabel(String metric) {
        return switch (metric) {
            case "doneRate", "completionRate" -> "完成率%";
            case "taskCount" -> "任务数";
            default -> "露出率%";
        };
    }

    private Map<Long, String> loadTopicNames() {
        return topicMapper.selectList(null).stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(GeoTopic::getId,
                        t -> StringUtils.hasText(t.getTopicName()) ? t.getTopicName() : ("话题" + t.getId()),
                        (a, b) -> a));
    }

    private String topicNameKey(GeoMonitorDaily d, Map<Long, String> names) {
        if (d.getTopicId() != null && names.containsKey(d.getTopicId())) {
            return names.get(d.getTopicId());
        }
        return "未分话题";
    }

    private String questionKey(GeoMonitorDaily d) {
        return StringUtils.hasText(d.getKeyword()) ? d.getKeyword().trim() : "未填目标问题";
    }

    private Map<Long, PersonRef> buildTopicPersonIndex(String personRole) {
        Map<Long, PersonRef> map = new HashMap<>();
        if ("owner".equals(personRole)) {
            return map;
        }
        List<GeoContentPlacement> placements = placementMapper.selectList(null);
        for (GeoContentPlacement p : placements) {
            if (p.getTopicId() == null) {
                continue;
            }
            PersonRef ref;
            if ("publisher".equals(personRole)) {
                if (p.getPublisherUserId() == null && !StringUtils.hasText(p.getPublisherName())) {
                    continue;
                }
                ref = new PersonRef(p.getPublisherUserId(),
                        StringUtils.hasText(p.getPublisherName()) ? p.getPublisherName() : ("用户" + p.getPublisherUserId()));
            } else {
                if (p.getOwnerUserId() == null && !StringUtils.hasText(p.getOwnerName())) {
                    continue;
                }
                ref = new PersonRef(p.getOwnerUserId(),
                        StringUtils.hasText(p.getOwnerName()) ? p.getOwnerName() : ("用户" + p.getOwnerUserId()));
            }
            map.putIfAbsent(p.getTopicId(), ref);
        }
        return map;
    }

    private PersonRef resolveGeoPerson(GeoMonitorDaily d, String personRole, Map<Long, PersonRef> topicPerson) {
        if ("owner".equals(personRole)) {
            if (d.getOwnerUserId() == null && !StringUtils.hasText(d.getOwnerName())) {
                return null;
            }
            return new PersonRef(d.getOwnerUserId(),
                    StringUtils.hasText(d.getOwnerName()) ? d.getOwnerName() : ("用户" + d.getOwnerUserId()));
        }
        if (d.getTopicId() != null && topicPerson.containsKey(d.getTopicId())) {
            return topicPerson.get(d.getTopicId());
        }
        return null;
    }

    private Map<Long, List<SysTaskAssignee>> loadAssignees(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        return assigneeMapper.selectList(new LambdaQueryWrapper<SysTaskAssignee>()
                        .in(SysTaskAssignee::getTaskId, taskIds)).stream()
                .collect(Collectors.groupingBy(SysTaskAssignee::getTaskId));
    }

    private static LocalDate defaultStart(LocalDate end, String grain) {
        return switch (grain) {
            case "day" -> end;
            case "month" -> end.withDayOfMonth(1);
            case "year" -> end.withDayOfYear(1);
            default -> end.minusDays(6);
        };
    }

    private static LocalDate parseDate(String raw, LocalDate fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        return LocalDate.parse(raw.trim());
    }

    private static String nz(String s, String d) {
        return StringUtils.hasText(s) ? s.trim() : d;
    }

    private static final class Agg {
        long sample;
        long hit;
    }

    private static final class PersonRef {
        final Long id;
        final String name;

        PersonRef(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        String label() {
            return name;
        }
    }

    private static final class BizTask {
        String themeKey;
        String themeLabel;
        String personLabel;
        String stage;
        LocalDateTime anchorTime;
        String bizType;
        Long bizId;
    }
}
