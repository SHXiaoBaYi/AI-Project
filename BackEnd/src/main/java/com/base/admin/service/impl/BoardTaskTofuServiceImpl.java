package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.BoardTaskTofuQueryDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoContentPlacementCite;
import com.base.admin.domain.entity.GeoContentPlacementItem;
import com.base.admin.domain.vo.BoardTaskTofuChartVO;
import com.base.admin.domain.vo.GeoChartPointVO;
import com.base.admin.domain.vo.GeoContentArticleDetailRowVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPlacementCiteMapper;
import com.base.admin.mapper.GeoContentPlacementItemMapper;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.service.BoardTaskTofuService;
import com.base.admin.service.DataScopeFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardTaskTofuServiceImpl implements BoardTaskTofuService {

    private static final DateTimeFormatter PUBLISH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GeoContentPlacementMapper placementMapper;
    private final GeoContentPlacementItemMapper itemMapper;
    private final GeoContentPlacementCiteMapper citeMapper;
    private final DataScopeFilter dataScopeFilter;

    @Override
    public BoardTaskTofuChartVO chart(BoardTaskTofuQueryDTO query) {
        BoardTaskTofuQueryDTO q = query == null ? new BoardTaskTofuQueryDTO() : query;
        String chartType = normalizeChartType(q.getChartType());
        String grain = normalizeGrain(q.getGrain());
        LocalDate[] range = dateRange(q);
        String level = resolveLevel(chartType, q);
        String seriesField = level;

        LocalDate loadStart = range[0];
        if ("employeeCiteMom".equals(chartType)) {
            loadStart = shiftBack(range[0], grain, estimateBuckets(range[0], range[1], grain) + 2);
        } else if ("employeeCiteYoy".equals(chartType)) {
            loadStart = range[0].minusYears(1);
        }

        Dataset data = loadDataset(loadStart, range[1], q);
        BoardTaskTofuChartVO vo = new BoardTaskTofuChartVO();
        vo.setChartType(chartType);
        vo.setGrain(grain);
        vo.setLevel(level);
        vo.setSeriesField(seriesField);
        vo.setTopicId(q.getTopicId());
        vo.setTargetQuestion(trimToNull(q.getTargetQuestion()));
        vo.setPublisherUserId(q.getPublisherUserId());
        vo.setPublisherName(trimToNull(q.getPublisherName()));
        vo.setContentPlatform(trimToNull(q.getContentPlatform()));
        vo.setAiPlatform(trimToNull(q.getAiPlatform()));
        vo.setMetricLabel(metricLabel(chartType));
        if (q.getTopicId() != null) {
            vo.setTopicName(data.topicNames.getOrDefault(q.getTopicId(), "话题#" + q.getTopicId()));
        }

        List<GeoChartPointVO> points = switch (chartType) {
            case "publishCount" -> buildPublishCount(data, range[0], range[1], grain, level, q);
            case "citeRate", "employeeCiteCompare" -> buildCiteRate(data, range[0], range[1], grain, level, q, null);
            case "employeeCiteMom" -> buildCiteRate(data, range[0], range[1], grain, level, q, "mom");
            case "employeeCiteYoy" -> buildCiteRate(data, range[0], range[1], grain, level, q, "yoy");
            case "topicCiteCount" -> buildCiteCount(data, range[0], range[1], grain, level, q);
            default -> List.of();
        };
        vo.setChart(points);
        return vo;
    }

    @Override
    public List<GeoContentArticleDetailRowVO> publishDetail(BoardTaskTofuQueryDTO query) {
        BoardTaskTofuQueryDTO q = query == null ? new BoardTaskTofuQueryDTO() : query;
        LocalDate[] range = dateRange(q);
        Dataset data = loadDataset(range[0], range[1], q);
        List<GeoContentArticleDetailRowVO> rows = new ArrayList<>();
        for (ItemRow row : data.items) {
            if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(row.item.getPublishStatus())) {
                continue;
            }
            if (!matchFilters(row, q, true)) {
                continue;
            }
            GeoContentArticleDetailRowVO vo = new GeoContentArticleDetailRowVO();
            vo.setPlacementId(row.placement.getId());
            vo.setItemId(row.item.getId());
            vo.setTargetQuestion(row.placement.getTargetQuestion());
            vo.setTitle(StringUtils.hasText(row.item.getTitle()) ? row.item.getTitle() : row.placement.getTitle());
            vo.setTopicName(row.placement.getTopicName());
            vo.setPublisherName(row.placement.getPublisherName());
            vo.setPublishPlatform(row.item.getPlatformName());
            vo.setContentForm(row.item.getContentForm());
            vo.setPublishStatus(row.item.getPublishStatus());
            vo.setPublishTime(row.item.getPublishTime() == null
                    ? null
                    : row.item.getPublishTime().format(PUBLISH_TIME));
            vo.setPublishUrl(row.item.getPublishUrl());
            vo.setCiteCount(data.citeCountByItem.getOrDefault(row.item.getId(), 0));
            rows.add(vo);
        }
        rows.sort(Comparator.comparing(GeoContentArticleDetailRowVO::getPublishTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    private List<GeoChartPointVO> buildPublishCount(Dataset data, LocalDate start, LocalDate end,
                                                    String grain, String level, BoardTaskTofuQueryDTO q) {
        Map<String, Integer> bag = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (ItemRow row : data.items) {
            if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(row.item.getPublishStatus())) {
                continue;
            }
            if (row.item.getPublishTime() == null
                    || row.item.getPublishTime().toLocalDate().isBefore(start)
                    || row.item.getPublishTime().toLocalDate().isAfter(end)) {
                continue;
            }
            if (!matchFilters(row, q, false)) {
                continue;
            }
            SeriesRef ref = seriesOf(row, null, level);
            if (ref == null) {
                continue;
            }
            String axis = timeBucket(row.item.getPublishTime().toLocalDate(), grain);
            String cell = axis + "\0" + ref.key;
            bag.merge(cell, 1, Integer::sum);
            labels.put(ref.key, ref.label);
        }
        return toPoints(bag, labels, false);
    }

    private List<GeoChartPointVO> buildCiteCount(Dataset data, LocalDate start, LocalDate end,
                                                  String grain, String level, BoardTaskTofuQueryDTO q) {
        Map<String, Integer> bag = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (CiteRow row : data.cites) {
            if (row.item.getPublishTime() == null
                    || row.item.getPublishTime().toLocalDate().isBefore(start)
                    || row.item.getPublishTime().toLocalDate().isAfter(end)) {
                continue;
            }
            if (!matchFilters(row.asItem(), q, false)) {
                continue;
            }
            if (StringUtils.hasText(q.getAiPlatform()) && !q.getAiPlatform().equals(trim(row.cite.getAiPlatform()))) {
                continue;
            }
            SeriesRef ref = seriesOf(row.asItem(), row.cite, level);
            if (ref == null) {
                continue;
            }
            String axis = timeBucket(row.item.getPublishTime().toLocalDate(), grain);
            String cell = axis + "\0" + ref.key;
            bag.merge(cell, 1, Integer::sum);
            labels.put(ref.key, ref.label);
        }
        return toPoints(bag, labels, false);
    }

    /**
     * deltaMode null=收录率；mom/yoy=百分点差。
     */
    private List<GeoChartPointVO> buildCiteRate(Dataset data, LocalDate start, LocalDate end,
                                                 String grain, String level, BoardTaskTofuQueryDTO q,
                                                 String deltaMode) {
        // axis\0seriesKey -> [successItemIds, citedItemIds]
        Map<String, Set<Long>> success = new HashMap<>();
        Map<String, Set<Long>> cited = new HashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        Set<Long> citedItemIds = data.cites.stream().map(c -> c.item.getId()).collect(Collectors.toSet());

        for (ItemRow row : data.items) {
            if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(row.item.getPublishStatus())
                    || row.item.getPublishTime() == null) {
                continue;
            }
            if (row.item.getPublishTime().toLocalDate().isBefore(start) || row.item.getPublishTime().toLocalDate().isAfter(end)) {
                if (deltaMode == null) {
                    continue;
                }
                // mom/yoy 需要扩展区间内的基期数据，基期过滤在后面做
            }
            if (!matchFilters(row, q, false)) {
                continue;
            }
            // AI 平台层：成功分母仍按当前过滤范围的成功投放；分子看是否被该 AI 引用
            if ("aiPlatform".equals(level)) {
                Set<String> aiOfItem = data.cites.stream()
                        .filter(c -> Objects.equals(c.item.getId(), row.item.getId()))
                        .map(c -> trim(c.cite.getAiPlatform()))
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (aiOfItem.isEmpty()) {
                    // 无引用也计入分母：用占位系列会扭曲，跳过无引用的到各 AI 系列
                    continue;
                }
                String axis = timeBucket(row.item.getPublishTime().toLocalDate(), grain);
                for (String ai : aiOfItem) {
                    if (StringUtils.hasText(q.getAiPlatform()) && !q.getAiPlatform().equals(ai)) {
                        continue;
                    }
                    String cell = axis + "\0" + ai;
                    success.computeIfAbsent(cell, k -> new HashSet<>()).add(row.item.getId());
                    cited.computeIfAbsent(cell, k -> new HashSet<>()).add(row.item.getId());
                    labels.put(ai, ai);
                }
                continue;
            }

            SeriesRef ref = seriesOf(row, null, level);
            if (ref == null) {
                continue;
            }
            String axis = timeBucket(row.item.getPublishTime().toLocalDate(), grain);
            String cell = axis + "\0" + ref.key;
            success.computeIfAbsent(cell, k -> new HashSet<>()).add(row.item.getId());
            if (citedItemIds.contains(row.item.getId())) {
                cited.computeIfAbsent(cell, k -> new HashSet<>()).add(row.item.getId());
            }
            labels.put(ref.key, ref.label);
        }

        // AI 平台层分母：同一日期桶下成功投放数（按员工等过滤后）
        if ("aiPlatform".equals(level)) {
            Map<String, Set<Long>> successByAxis = new HashMap<>();
            for (ItemRow row : data.items) {
                if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(row.item.getPublishStatus())
                        || row.item.getPublishTime() == null) {
                    continue;
                }
                if (!matchFilters(row, q, false)) {
                    continue;
                }
                String axis = timeBucket(row.item.getPublishTime().toLocalDate(), grain);
                successByAxis.computeIfAbsent(axis, k -> new HashSet<>()).add(row.item.getId());
            }
            Map<String, Set<Long>> citedByCell = new HashMap<>();
            for (CiteRow row : data.cites) {
                if (row.item.getPublishTime() == null) {
                    continue;
                }
                if (!matchFilters(row.asItem(), q, false)) {
                    continue;
                }
                String ai = trim(row.cite.getAiPlatform());
                if (!StringUtils.hasText(ai)) {
                    continue;
                }
                if (StringUtils.hasText(q.getAiPlatform()) && !q.getAiPlatform().equals(ai)) {
                    continue;
                }
                String axis = timeBucket(row.item.getPublishTime().toLocalDate(), grain);
                String cell = axis + "\0" + ai;
                citedByCell.computeIfAbsent(cell, k -> new HashSet<>()).add(row.item.getId());
                labels.put(ai, ai);
            }
            success.clear();
            cited.clear();
            for (Map.Entry<String, Set<Long>> e : citedByCell.entrySet()) {
                String axis = e.getKey().split("\0", 2)[0];
                success.put(e.getKey(), new HashSet<>(successByAxis.getOrDefault(axis, Set.of())));
                cited.put(e.getKey(), e.getValue());
            }
        }

        Map<String, Double> rateByCell = new LinkedHashMap<>();
        Set<String> cells = new LinkedHashSet<>();
        cells.addAll(success.keySet());
        cells.addAll(cited.keySet());
        for (String cell : cells) {
            int s = success.getOrDefault(cell, Set.of()).size();
            int c = cited.getOrDefault(cell, Set.of()).size();
            rateByCell.put(cell, s == 0 ? 0D : round2(c * 100.0 / s));
        }

        if (deltaMode == null) {
            Map<String, Integer> fake = new LinkedHashMap<>();
            // use double via toPointsRate
            return toRatePoints(rateByCell, labels, start, end, grain);
        }

        Map<String, Double> delta = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : rateByCell.entrySet()) {
            String[] parts = e.getKey().split("\0", 2);
            String axis = parts[0];
            String key = parts.length > 1 ? parts[1] : "-";
            LocalDate axisDate = parseBucketStart(axis, grain);
            if (axisDate == null || axisDate.isBefore(start) || axisDate.isAfter(end)) {
                continue;
            }
            String baseAxis = "mom".equals(deltaMode)
                    ? timeBucket(shiftBack(axisDate, grain, 1), grain)
                    : timeBucket(axisDate.minusYears(1), grain);
            Double base = rateByCell.get(baseAxis + "\0" + key);
            if (base == null) {
                continue;
            }
            delta.put(e.getKey(), round2(e.getValue() - base));
        }
        return toRatePoints(delta, labels, start, end, grain);
    }

    private boolean matchFilters(ItemRow row, BoardTaskTofuQueryDTO q, boolean requireContentPlatform) {
        if (q.getTopicId() != null && !Objects.equals(row.placement.getTopicId(), q.getTopicId())) {
            return false;
        }
        String tq = trimToNull(q.getTargetQuestion());
        if (tq != null && !tq.equals(trim(row.placement.getTargetQuestion()))) {
            return false;
        }
        if (q.getPublisherUserId() != null
                && !Objects.equals(row.placement.getPublisherUserId(), q.getPublisherUserId())) {
            return false;
        }
        String pn = trimToNull(q.getPublisherName());
        if (pn != null && q.getPublisherUserId() == null
                && !pn.equals(trim(row.placement.getPublisherName()))) {
            return false;
        }
        String cp = trimToNull(q.getContentPlatform());
        if (cp != null) {
            if (!cp.equals(trim(row.item.getPlatformName()))) {
                return false;
            }
        } else if (requireContentPlatform) {
            // drawer at content platform leaf should pass platform
        }
        return true;
    }

    private SeriesRef seriesOf(ItemRow row, GeoContentPlacementCite cite, String level) {
        return switch (level) {
            case "topic" -> {
                Long tid = row.placement.getTopicId();
                if (tid == null) {
                    yield new SeriesRef("0", "未分话题");
                }
                yield new SeriesRef(String.valueOf(tid),
                        StringUtils.hasText(row.placement.getTopicName())
                                ? row.placement.getTopicName() : ("话题#" + tid));
            }
            case "question" -> {
                String kw = StringUtils.hasText(row.placement.getTargetQuestion())
                        ? row.placement.getTargetQuestion().trim() : "未填目标问题";
                yield new SeriesRef(kw, kw);
            }
            case "employee" -> {
                Long uid = row.placement.getPublisherUserId();
                String name = StringUtils.hasText(row.placement.getPublisherName())
                        ? row.placement.getPublisherName().trim() : "未分配";
                yield new SeriesRef(uid == null ? ("n:" + name) : String.valueOf(uid), name);
            }
            case "contentPlatform" -> {
                String p = StringUtils.hasText(row.item.getPlatformName())
                        ? row.item.getPlatformName().trim() : "未知平台";
                yield new SeriesRef(p, p);
            }
            case "aiPlatform" -> {
                String ai = cite != null && StringUtils.hasText(cite.getAiPlatform())
                        ? cite.getAiPlatform().trim() : null;
                if (ai == null) {
                    yield null;
                }
                yield new SeriesRef(ai, ai);
            }
            default -> null;
        };
    }

    private String resolveLevel(String chartType, BoardTaskTofuQueryDTO q) {
        return switch (chartType) {
            case "publishCount" -> {
                if (StringUtils.hasText(q.getContentPlatform())) {
                    yield "contentPlatform";
                }
                if (q.getPublisherUserId() != null || StringUtils.hasText(q.getPublisherName())) {
                    yield "contentPlatform";
                }
                if (StringUtils.hasText(q.getTargetQuestion())) {
                    yield "employee";
                }
                if (q.getTopicId() != null) {
                    yield "question";
                }
                yield "topic";
            }
            case "citeRate" -> {
                if (StringUtils.hasText(q.getAiPlatform())) {
                    yield "aiPlatform";
                }
                if (q.getPublisherUserId() != null || StringUtils.hasText(q.getPublisherName())) {
                    yield "aiPlatform";
                }
                if (StringUtils.hasText(q.getTargetQuestion())) {
                    yield "employee";
                }
                if (q.getTopicId() != null) {
                    yield "question";
                }
                yield "topic";
            }
            case "employeeCiteCompare", "employeeCiteMom", "employeeCiteYoy" -> {
                if (q.getPublisherUserId() != null || StringUtils.hasText(q.getPublisherName())) {
                    yield "aiPlatform";
                }
                yield "employee";
            }
            case "topicCiteCount" -> {
                if (StringUtils.hasText(q.getAiPlatform())) {
                    yield "aiPlatform";
                }
                if (StringUtils.hasText(q.getTargetQuestion())) {
                    yield "aiPlatform";
                }
                if (q.getTopicId() != null) {
                    yield "question";
                }
                yield "topic";
            }
            default -> "topic";
        };
    }

    private Dataset loadDataset(LocalDate start, LocalDate end, BoardTaskTofuQueryDTO q) {
        LambdaQueryWrapper<GeoContentPlacement> pw = new LambdaQueryWrapper<GeoContentPlacement>()
                .eq(q.getTopicId() != null, GeoContentPlacement::getTopicId, q.getTopicId())
                .eq(q.getPublisherUserId() != null, GeoContentPlacement::getPublisherUserId, q.getPublisherUserId());
        if (StringUtils.hasText(q.getTargetQuestion())) {
            pw.eq(GeoContentPlacement::getTargetQuestion, q.getTargetQuestion().trim());
        }
        if (StringUtils.hasText(q.getPublisherName()) && q.getPublisherUserId() == null) {
            pw.eq(GeoContentPlacement::getPublisherName, q.getPublisherName().trim());
        }
        dataScopeFilter.applyGeoPlacement(pw);
        List<GeoContentPlacement> placements = placementMapper.selectList(pw);
        Map<Long, GeoContentPlacement> placementMap = placements.stream()
                .collect(Collectors.toMap(GeoContentPlacement::getId, p -> p, (a, b) -> a));
        Map<Long, String> topicNames = new HashMap<>();
        for (GeoContentPlacement p : placements) {
            if (p.getTopicId() != null) {
                topicNames.put(p.getTopicId(),
                        StringUtils.hasText(p.getTopicName()) ? p.getTopicName() : ("话题#" + p.getTopicId()));
            }
        }
        if (placementMap.isEmpty()) {
            return new Dataset(List.of(), List.of(), Map.of(), topicNames);
        }

        LambdaQueryWrapper<GeoContentPlacementItem> iw = new LambdaQueryWrapper<GeoContentPlacementItem>()
                .in(GeoContentPlacementItem::getPlacementId, placementMap.keySet())
                .ge(GeoContentPlacementItem::getPublishTime, start.atStartOfDay())
                .lt(GeoContentPlacementItem::getPublishTime, end.plusDays(1).atStartOfDay());
        if (StringUtils.hasText(q.getContentPlatform())) {
            iw.eq(GeoContentPlacementItem::getPlatformName, q.getContentPlatform().trim());
        }
        var snap = dataScopeFilter.snapshot();
        if (!snap.globalAll() && snap.geoEnabled() && !snap.geoPlatformNames().isEmpty()) {
            iw.in(GeoContentPlacementItem::getPlatformName, snap.geoPlatformNames());
        }
        List<ItemRow> items = new ArrayList<>();
        for (GeoContentPlacementItem item : itemMapper.selectList(iw)) {
            GeoContentPlacement p = placementMap.get(item.getPlacementId());
            if (p != null) {
                items.add(new ItemRow(p, item));
            }
        }

        Set<Long> itemIds = items.stream().map(r -> r.item.getId()).collect(Collectors.toSet());
        Set<Long> placementIds = placementMap.keySet();
        List<CiteRow> cites = new ArrayList<>();
        Map<Long, Integer> citeCountByItem = new HashMap<>();
        if (!itemIds.isEmpty()) {
            LambdaQueryWrapper<GeoContentPlacementCite> cw = new LambdaQueryWrapper<GeoContentPlacementCite>()
                    .and(w -> w.in(GeoContentPlacementCite::getItemId, itemIds)
                            .or()
                            .in(GeoContentPlacementCite::getPlacementId, placementIds));
            if (StringUtils.hasText(q.getAiPlatform())) {
                cw.eq(GeoContentPlacementCite::getAiPlatform, q.getAiPlatform().trim());
            }
            Map<Long, ItemRow> itemMap = items.stream()
                    .collect(Collectors.toMap(r -> r.item.getId(), r -> r, (a, b) -> a));
            Map<Long, List<ItemRow>> itemsByPlacement = items.stream()
                    .collect(Collectors.groupingBy(r -> r.placement.getId()));
            for (GeoContentPlacementCite cite : citeMapper.selectList(cw)) {
                if (cite.getItemId() != null) {
                    ItemRow itemRow = itemMap.get(cite.getItemId());
                    if (itemRow == null || !Constants.CONTENT_PUBLISH_SUCCESS.equals(itemRow.item.getPublishStatus())) {
                        continue;
                    }
                    cites.add(new CiteRow(itemRow.placement, itemRow.item, cite));
                    citeCountByItem.merge(itemRow.item.getId(), 1, Integer::sum);
                    continue;
                }
                // 仅挂在投放上的引用：落到该投放在统计期内、已投放成功的各平台文章上
                if (cite.getPlacementId() == null) {
                    continue;
                }
                List<ItemRow> placementItems = itemsByPlacement.getOrDefault(cite.getPlacementId(), List.of());
                for (ItemRow itemRow : placementItems) {
                    if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(itemRow.item.getPublishStatus())) {
                        continue;
                    }
                    cites.add(new CiteRow(itemRow.placement, itemRow.item, cite));
                    citeCountByItem.merge(itemRow.item.getId(), 1, Integer::sum);
                }
            }
        }
        return new Dataset(items, cites, citeCountByItem, topicNames);
    }

    private static List<GeoChartPointVO> toPoints(Map<String, Integer> bag, Map<String, String> labels, boolean unused) {
        List<GeoChartPointVO> list = new ArrayList<>();
        bag.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String[] parts = e.getKey().split("\0", 2);
                    String key = parts.length > 1 ? parts[1] : "-";
                    GeoChartPointVO p = new GeoChartPointVO();
                    p.setAxis(parts[0]);
                    p.setSeries(labels.getOrDefault(key, key));
                    p.setKey(key);
                    p.setValue(e.getValue().doubleValue());
                    list.add(p);
                });
        return list;
    }

    private static List<GeoChartPointVO> toRatePoints(Map<String, Double> bag, Map<String, String> labels,
                                                       LocalDate start, LocalDate end, String grain) {
        List<GeoChartPointVO> list = new ArrayList<>();
        bag.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String[] parts = e.getKey().split("\0", 2);
                    String axis = parts[0];
                    LocalDate axisDate = parseBucketStart(axis, grain);
                    if (axisDate != null && (axisDate.isBefore(start) || axisDate.isAfter(end))) {
                        return;
                    }
                    String key = parts.length > 1 ? parts[1] : "-";
                    GeoChartPointVO p = new GeoChartPointVO();
                    p.setAxis(axis);
                    p.setSeries(labels.getOrDefault(key, key));
                    p.setKey(key);
                    p.setValue(e.getValue());
                    list.add(p);
                });
        return list;
    }

    private static String normalizeChartType(String raw) {
        String t = raw == null ? "" : raw.trim();
        return switch (t) {
            case "publishCount", "citeRate", "employeeCiteCompare", "employeeCiteMom",
                 "employeeCiteYoy", "topicCiteCount" -> t;
            default -> throw new BusinessException("不支持的图表类型: " + raw);
        };
    }

    private static String normalizeGrain(String raw) {
        String g = raw == null ? "week" : raw.trim().toLowerCase(Locale.ROOT);
        return List.of("day", "week", "month", "year").contains(g) ? g : "week";
    }

    private static String metricLabel(String chartType) {
        return switch (chartType) {
            case "publishCount" -> "发布数量";
            case "citeRate" -> "AI收录率%";
            case "employeeCiteCompare" -> "员工AI收录率%";
            case "employeeCiteMom" -> "收录率环比(pp)";
            case "employeeCiteYoy" -> "收录率同比(pp)";
            case "topicCiteCount" -> "AI引用数";
            default -> "指标";
        };
    }

    private static LocalDate[] dateRange(BoardTaskTofuQueryDTO q) {
        LocalDate end = parseDate(q.getEndDate());
        LocalDate start = parseDate(q.getStartDate());
        if (end == null) {
            end = LocalDate.now();
        }
        if (start == null) {
            start = end.minusDays(27);
        }
        if (start.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        return new LocalDate[]{start, end};
    }

    private static LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim().substring(0, Math.min(10, raw.trim().length())));
        } catch (Exception e) {
            return null;
        }
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

    private static LocalDate parseBucketStart(String axis, String grain) {
        try {
            return switch (grain) {
                case "month" -> LocalDate.parse(axis + "-01");
                case "year" -> LocalDate.of(Integer.parseInt(axis), 1, 1);
                case "week" -> {
                    String[] p = axis.split("-W");
                    int y = Integer.parseInt(p[0]);
                    int w = Integer.parseInt(p[1]);
                    yield LocalDate.of(y, 1, 1)
                            .with(WeekFields.of(Locale.CHINA).weekOfWeekBasedYear(), w)
                            .with(WeekFields.of(Locale.CHINA).dayOfWeek(), 1);
                }
                default -> LocalDate.parse(axis);
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate shiftBack(LocalDate date, String grain, int steps) {
        int n = Math.max(1, steps);
        return switch (grain) {
            case "month" -> date.minusMonths(n);
            case "year" -> date.minusYears(n);
            case "week" -> date.minusWeeks(n);
            default -> date.minusDays(n);
        };
    }

    private static int estimateBuckets(LocalDate start, LocalDate end, String grain) {
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        return switch (grain) {
            case "month" -> (int) Math.max(1, days / 28);
            case "year" -> (int) Math.max(1, days / 365);
            case "week" -> (int) Math.max(1, days / 7);
            default -> (int) days;
        };
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private record SeriesRef(String key, String label) {}

    private record ItemRow(GeoContentPlacement placement, GeoContentPlacementItem item) {}

    private record CiteRow(GeoContentPlacement placement, GeoContentPlacementItem item, GeoContentPlacementCite cite) {
        ItemRow asItem() {
            return new ItemRow(placement, item);
        }
    }

    private record Dataset(List<ItemRow> items, List<CiteRow> cites, Map<Long, Integer> citeCountByItem,
                           Map<Long, String> topicNames) {}
}
