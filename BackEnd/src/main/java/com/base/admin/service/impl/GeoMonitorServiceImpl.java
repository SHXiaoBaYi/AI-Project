package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoBoardQueryDTO;
import com.base.admin.domain.dto.GeoDailyBatchDTO;
import com.base.admin.domain.dto.GeoDailyDTO;
import com.base.admin.domain.dto.GeoDailyPlatformItemDTO;
import com.base.admin.domain.dto.GeoDailyQueryDTO;
import com.base.admin.domain.dto.GeoYearTargetDTO;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.GeoYearTarget;
import com.base.admin.domain.vo.GeoChartPointVO;
import com.base.admin.domain.vo.GeoDailyBoardVO;
import com.base.admin.domain.vo.GeoDailyGroupVO;
import com.base.admin.domain.vo.GeoDailyVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoLatestDateVO;
import com.base.admin.domain.vo.GeoWeeklyBoardVO;
import com.base.admin.domain.vo.GeoYearlyBoardVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoMonitorDailyMapper;
import com.base.admin.mapper.GeoTopicMapper;
import com.base.admin.mapper.GeoYearTargetMapper;
import com.base.admin.service.GeoMonitorService;
import com.base.admin.service.GeoPlatformService;
import com.base.admin.service.GeoTopicService;
import com.base.admin.util.ExcelCellUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeoMonitorServiceImpl implements GeoMonitorService {

    private static final int DATE_START_COL = 4;
    private static final int COLS_PER_DATE = 12;
    private static final int PLATFORMS_PER_METRIC = 2;

    private final GeoMonitorDailyMapper dailyMapper;
    private final GeoTopicMapper topicMapper;
    private final GeoYearTargetMapper targetMapper;
    private final GeoTopicService topicService;
    private final GeoPlatformService platformService;

    @Override
    public PageResult<GeoDailyVO> listDaily(GeoDailyQueryDTO query) {
        LambdaQueryWrapper<GeoMonitorDaily> wrapper = buildDailyWrapper(
                query.getStartDate(), query.getEndDate(), query.getTopicId(), query.getKeyword(), query.getPlatforms());
        wrapper.orderByDesc(GeoMonitorDaily::getInspectDate)
                .orderByDesc(GeoMonitorDaily::getUpdateTime)
                .orderByAsc(GeoMonitorDaily::getId);
        Page<GeoMonitorDaily> page = dailyMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, String> topicNames = topicNameMap();
        List<GeoDailyVO> rows = page.getRecords().stream().map(e -> toVo(e, topicNames)).toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public GeoDailyVO getDaily(Long id) {
        return toVo(requireDaily(id), topicNameMap());
    }

    @Override
    @Transactional
    public void createDaily(GeoDailyDTO dto) {
        upsertDaily(dto, true);
    }

    @Override
    @Transactional
    public void updateDaily(GeoDailyDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("缺少主键");
        }
        upsertDaily(dto, false);
    }

    @Override
    @Transactional
    public void deleteDaily(Long id) {
        requireDaily(id);
        dailyMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void saveDailyBatch(GeoDailyBatchDTO dto) {
        int saved = 0;
        for (GeoDailyPlatformItemDTO item : dto.getItems()) {
            if (item == null || !StringUtils.hasText(item.getPlatform())) {
                continue;
            }
            GeoDailyDTO one = new GeoDailyDTO();
            one.setId(item.getId());
            one.setInspectDate(dto.getInspectDate());
            one.setTopicId(dto.getTopicId());
            one.setKeyword(dto.getKeyword());
            one.setPlatform(item.getPlatform());
            one.setMentioned(item.getMentioned());
            one.setRankNo(item.getRankNo());
            one.setRecommendStatus(item.getRecommendStatus());
            one.setScreenshotUrl(item.getScreenshotUrl());
            one.setThirdPartyUrl(item.getThirdPartyUrl());
            one.setNegativeContent(item.getNegativeContent());
            one.setCompetitors(item.getCompetitors());
            upsertDaily(one, true);
            saved++;
        }
        if (saved == 0) {
            throw new BusinessException("请至少填写一个平台");
        }
    }

    @Override
    public GeoDailyGroupVO getDailyGroup(LocalDate inspectDate, String keyword) {
        if (inspectDate == null || !StringUtils.hasText(keyword)) {
            throw new BusinessException("巡查日期和关键字不能为空");
        }
        List<GeoMonitorDaily> records = dailyMapper.selectList(new LambdaQueryWrapper<GeoMonitorDaily>()
                .eq(GeoMonitorDaily::getInspectDate, inspectDate)
                .eq(GeoMonitorDaily::getKeyword, keyword.trim())
                .orderByAsc(GeoMonitorDaily::getId));
        Map<Long, String> topicNames = topicNameMap();
        GeoDailyGroupVO group = new GeoDailyGroupVO();
        group.setInspectDate(inspectDate);
        group.setKeyword(keyword.trim());
        if (!records.isEmpty()) {
            GeoMonitorDaily first = records.getFirst();
            group.setTopicId(first.getTopicId());
            group.setTopicName(topicNames.getOrDefault(first.getTopicId(), ""));
        }
        group.setItems(records.stream().map(e -> toVo(e, topicNames)).toList());
        return group;
    }

    @Override
    public GeoLatestDateVO latestInspectDate() {
        Page<GeoMonitorDaily> page = dailyMapper.selectPage(
                new Page<>(1, 1, false),
                new LambdaQueryWrapper<GeoMonitorDaily>()
                        .orderByDesc(GeoMonitorDaily::getUpdateTime)
                        .orderByDesc(GeoMonitorDaily::getInspectDate));
        GeoLatestDateVO vo = new GeoLatestDateVO();
        if (!page.getRecords().isEmpty()) {
            vo.setInspectDate(page.getRecords().getFirst().getInspectDate());
        }
        return vo;
    }

    @Override
    @Transactional
    public GeoImportResultVO importDaily(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        try {
            return importDaily(file.getInputStream());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Excel 解析失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public GeoImportResultVO importDaily(java.io.InputStream inputStream) {
        GeoImportResultVO result = new GeoImportResultVO();
        try (Workbook wb = WorkbookFactory.create(inputStream)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            int lastCol = 0;
            if (sheet.getRow(2) != null) {
                lastCol = sheet.getRow(2).getLastCellNum();
            }
            List<DateGroup> groups = parseDateGroups(sheet, lastCol);
            String lastTopic = "";
            String lastWeek = "";
            for (int r = 3; r <= lastRow; r++) {
                String keyword = ExcelCellUtils.str(sheet, r, 3);
                if (!StringUtils.hasText(keyword)) {
                    continue;
                }
                String week = ExcelCellUtils.str(sheet, r, 1);
                String topicName = ExcelCellUtils.str(sheet, r, 2);
                if (StringUtils.hasText(week)) {
                    lastWeek = week;
                }
                if (StringUtils.hasText(topicName)) {
                    lastTopic = topicName;
                }
                GeoTopic topic = topicService.getOrCreate(lastTopic, lastWeek);
                for (DateGroup group : groups) {
                    for (int p = 0; p < group.platforms.size(); p++) {
                        result.setTotalCount(result.getTotalCount() + 1);
                        try {
                            GeoDailyDTO dto = new GeoDailyDTO();
                            dto.setInspectDate(group.date);
                            dto.setPlatform(group.platforms.get(p));
                            platformService.getOrCreate(dto.getPlatform());
                            dto.setKeyword(keyword.trim());
                            dto.setTopicId(topic.getId());
                            dto.setMentioned(parseMentioned(ExcelCellUtils.str(sheet, r, group.startCol + p)));
                            dto.setRankNo(parseRank(ExcelCellUtils.str(sheet, r, group.startCol + 2 + p)));
                            dto.setRecommendStatus(ExcelCellUtils.str(sheet, r, group.startCol + 4 + p));
                            String shot = ExcelCellUtils.str(sheet, r, group.startCol + 6 + p);
                            if (isHttp(shot)) {
                                dto.setThirdPartyUrl(shot);
                            }
                            dto.setNegativeContent(ExcelCellUtils.str(sheet, r, group.startCol + 8 + p));
                            dto.setCompetitors(ExcelCellUtils.str(sheet, r, group.startCol + 10 + p));
                            boolean inserted = upsertDaily(dto, true);
                            if (inserted) {
                                result.setInsertCount(result.getInsertCount() + 1);
                            } else {
                                result.setUpdateCount(result.getUpdateCount() + 1);
                            }
                        } catch (Exception e) {
                            result.setFailureCount(result.getFailureCount() + 1);
                            result.getErrors().add(new GeoImportResultVO.GeoImportErrorVO(
                                    r + 1, group.platforms.get(p), e.getMessage()));
                        }
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Excel 解析失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public List<String> listPlatforms() {
        return platformService.listAll().stream().map(p -> p.getPlatformName()).toList();
    }

    @Override
    public GeoWeeklyBoardVO weeklyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = weekRange(query);
        List<GeoMonitorDaily> records = dailyMapper.selectList(
                buildDailyWrapper(range[0], range[1], query.getTopicId(), query.getKeyword(), query.getPlatforms()));
        Map<Long, String> topicNames = topicNameMap();
        GeoWeeklyBoardVO board = new GeoWeeklyBoardVO();
        for (TrendRow row : aggregateTrend(records, GeoMonitorServiceImpl::weekLabel, topicNames)) {
            GeoWeeklyBoardVO.GeoWeeklyRowVO vo = new GeoWeeklyBoardVO.GeoWeeklyRowVO();
            vo.setWeekLabel(row.axis);
            vo.setTopicName(row.topicName);
            vo.setPlatform(row.platform);
            vo.setSampleCount(row.sampleCount);
            vo.setMentionRate(row.mentionRate);
            vo.setFirstMentionRate(row.firstMentionRate);
            vo.setRecommendCount(row.recommendCount);
            vo.setCompetitorTop(row.competitorTop);
            vo.setCitePlatformTop(row.citePlatformTop);
            board.getRows().add(vo);
        }
        fillMetricCharts(records, GeoMonitorServiceImpl::weekLabel,
                board.getMentionChart(), board.getFirstMentionChart(), board.getRecommendChart());
        return board;
    }

    @Override
    public GeoDailyBoardVO dailyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = dayRange(query);
        List<GeoMonitorDaily> records = dailyMapper.selectList(
                buildDailyWrapper(range[0], range[1], query.getTopicId(), query.getKeyword(), query.getPlatforms()));
        Map<Long, String> topicNames = topicNameMap();
        GeoDailyBoardVO board = new GeoDailyBoardVO();
        for (TrendRow row : aggregateTrend(records, r -> r.getInspectDate().toString(), topicNames)) {
            GeoDailyBoardVO.GeoDailyRowVO vo = new GeoDailyBoardVO.GeoDailyRowVO();
            vo.setDateLabel(row.axis);
            vo.setTopicName(row.topicName);
            vo.setPlatform(row.platform);
            vo.setSampleCount(row.sampleCount);
            vo.setMentionRate(row.mentionRate);
            vo.setFirstMentionRate(row.firstMentionRate);
            vo.setRecommendCount(row.recommendCount);
            vo.setCompetitorTop(row.competitorTop);
            vo.setCitePlatformTop(row.citePlatformTop);
            board.getRows().add(vo);
        }
        fillMetricCharts(records, r -> r.getInspectDate().toString(),
                board.getMentionChart(), board.getFirstMentionChart(), board.getRecommendChart());
        return board;
    }

    @Override
    public GeoYearlyBoardVO yearlyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = yearRange(query);
        LocalDate start = range[0];
        LocalDate end = range[1];
        List<GeoMonitorDaily> records = dailyMapper.selectList(
                buildDailyWrapper(start, end, query.getTopicId(), query.getKeyword(), query.getPlatforms()));
        Map<Long, String> topicNames = topicNameMap();
        List<GeoYearTarget> targets = targetMapper.selectList(new LambdaQueryWrapper<GeoYearTarget>()
                .eq(query.getTopicId() != null, GeoYearTarget::getTopicId, query.getTopicId())
                .orderByAsc(GeoYearTarget::getSortOrder));

        GeoYearlyBoardVO board = new GeoYearlyBoardVO();
        if (!targets.isEmpty()) {
            for (GeoYearTarget target : targets) {
                if (!overlaps(target, start, end)) {
                    continue;
                }
                LocalDate ts = target.getPeriodStart() != null ? target.getPeriodStart() : start;
                LocalDate te = target.getPeriodEnd() != null ? target.getPeriodEnd() : end;
                List<GeoMonitorDaily> scoped = records.stream()
                        .filter(r -> r.getTopicId().equals(target.getTopicId()))
                        .filter(r -> !r.getInspectDate().isBefore(ts) && !r.getInspectDate().isAfter(te))
                        .toList();
                Map<String, List<GeoMonitorDaily>> byPlatform = scoped.stream()
                        .collect(Collectors.groupingBy(GeoMonitorDaily::getPlatform));
                if (byPlatform.isEmpty()) {
                    appendYearly(board, target.getPeriodLabel(), topicNames.getOrDefault(target.getTopicId(), ""),
                            "-", target.getTargetRate(), List.of());
                } else {
                    byPlatform.forEach((platform, list) -> appendYearly(
                            board, target.getPeriodLabel(), topicNames.getOrDefault(target.getTopicId(), ""),
                            platform, target.getTargetRate(), list));
                }
            }
        } else {
            Map<String, List<GeoMonitorDaily>> grouped = records.stream()
                    .collect(Collectors.groupingBy(r -> r.getInspectDate().getYear() + "\0" + r.getTopicId() + "\0" + r.getPlatform()));
            grouped.forEach((key, list) -> {
                GeoMonitorDaily first = list.getFirst();
                appendYearly(board, String.valueOf(first.getInspectDate().getYear()),
                        topicNames.getOrDefault(first.getTopicId(), ""), first.getPlatform(),
                        new BigDecimal("80.00"), list);
            });
        }
        board.getRows().sort(Comparator.comparing(GeoYearlyBoardVO.GeoYearlyRowVO::getPeriodLabel)
                .thenComparing(GeoYearlyBoardVO.GeoYearlyRowVO::getTopicName)
                .thenComparing(GeoYearlyBoardVO.GeoYearlyRowVO::getPlatform));
        sortChart(board.getActualChart());
        sortChart(board.getAchieveChart());
        return board;
    }

    @Override
    public List<GeoYearTarget> listTargets() {
        return targetMapper.selectList(new LambdaQueryWrapper<GeoYearTarget>().orderByAsc(GeoYearTarget::getSortOrder));
    }

    @Override
    @Transactional
    public void saveTarget(GeoYearTargetDTO dto) {
        if (dto.getId() == null) {
            GeoYearTarget entity = new GeoYearTarget();
            fillTarget(entity, dto);
            targetMapper.insert(entity);
            return;
        }
        GeoYearTarget entity = targetMapper.selectById(dto.getId());
        if (entity == null) {
            throw new BusinessException("目标配置不存在");
        }
        fillTarget(entity, dto);
        targetMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void deleteTarget(Long id) {
        targetMapper.deleteById(id);
    }

    private void fillTarget(GeoYearTarget entity, GeoYearTargetDTO dto) {
        topicService.getById(dto.getTopicId());
        entity.setPeriodLabel(dto.getPeriodLabel());
        entity.setPeriodStart(dto.getPeriodStart());
        entity.setPeriodEnd(dto.getPeriodEnd());
        entity.setTopicId(dto.getTopicId());
        entity.setTargetRate(dto.getTargetRate());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        entity.setRemark(dto.getRemark());
    }

    private void appendYearly(GeoYearlyBoardVO board, String period, String topic, String platform,
                              BigDecimal targetRate, List<GeoMonitorDaily> list) {
        int total = list.size();
        int mentioned = (int) list.stream().filter(x -> Objects.equals(x.getMentioned(), 1)).count();
        double actual = pct(mentioned, total);
        double achieve = targetRate == null || targetRate.doubleValue() == 0 ? 0 : actual / targetRate.doubleValue() * 100;
        GeoYearlyBoardVO.GeoYearlyRowVO row = new GeoYearlyBoardVO.GeoYearlyRowVO();
        row.setPeriodLabel(period);
        row.setTopicName(topic);
        row.setPlatform(platform);
        row.setTargetRate(targetRate);
        row.setActualRate(round(actual));
        row.setAchieveRate(round(achieve));
        row.setSampleCount(total);
        board.getRows().add(row);
        addChart(board.getActualChart(), period + " / " + topic, platform, row.getActualRate());
        addChart(board.getAchieveChart(), period + " / " + topic, platform, row.getAchieveRate());
    }

    private boolean upsertDaily(GeoDailyDTO dto, boolean allowInsert) {
        topicService.getById(dto.getTopicId());
        platformService.getOrCreate(dto.getPlatform());
        String platform = dto.getPlatform().trim();
        String keyword = dto.getKeyword().trim();
        GeoMonitorDaily existing = dailyMapper.selectUkIncludeDeleted(dto.getInspectDate(), platform, keyword);
        if (existing == null && dto.getId() != null) {
            existing = dailyMapper.selectById(dto.getId());
            if (existing == null) {
                existing = requireDaily(dto.getId());
            }
        }
        if (existing != null && dto.getId() != null && !existing.getId().equals(dto.getId())
                && Integer.valueOf(1).equals(existing.getIsActive())) {
            throw new BusinessException("同一天、同一平台、同一关键字已存在记录");
        }
        if (existing == null) {
            if (!allowInsert) {
                throw new BusinessException("监测数据不存在");
            }
            GeoMonitorDaily entity = new GeoMonitorDaily();
            fillDaily(entity, dto);
            dailyMapper.insert(entity);
            return true;
        }
        if (existing.getIsActive() == null || existing.getIsActive() == 0) {
            dailyMapper.restoreActive(existing.getId());
            existing.setIsActive(1);
        }
        fillDaily(existing, dto);
        dailyMapper.updateById(existing);
        return false;
    }

    private void fillDaily(GeoMonitorDaily entity, GeoDailyDTO dto) {
        entity.setInspectDate(dto.getInspectDate());
        entity.setPlatform(dto.getPlatform().trim());
        entity.setKeyword(dto.getKeyword().trim());
        entity.setTopicId(dto.getTopicId());
        entity.setMentioned(dto.getMentioned() != null ? dto.getMentioned() : 0);
        entity.setRankNo(dto.getRankNo());
        entity.setRecommendStatus(dto.getRecommendStatus());
        if (dto.getScreenshotUrl() != null) {
            entity.setScreenshotUrl(dto.getScreenshotUrl());
        }
        entity.setThirdPartyUrl(dto.getThirdPartyUrl());
        entity.setNegativeContent(dto.getNegativeContent());
        entity.setCompetitors(dto.getCompetitors());
    }

    private GeoMonitorDaily requireDaily(Long id) {
        GeoMonitorDaily entity = dailyMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("监测数据不存在");
        }
        return entity;
    }

    private LambdaQueryWrapper<GeoMonitorDaily> buildDailyWrapper(
            LocalDate start, LocalDate end, Long topicId, String keyword, List<String> platforms) {
        List<String> platformFilter = platforms == null ? List.of()
                : platforms.stream().filter(StringUtils::hasText).toList();
        return new LambdaQueryWrapper<GeoMonitorDaily>()
                .ge(start != null, GeoMonitorDaily::getInspectDate, start)
                .le(end != null, GeoMonitorDaily::getInspectDate, end)
                .eq(topicId != null, GeoMonitorDaily::getTopicId, topicId)
                .like(StringUtils.hasText(keyword), GeoMonitorDaily::getKeyword, keyword)
                .in(!platformFilter.isEmpty(), GeoMonitorDaily::getPlatform, platformFilter);
    }

    private Map<Long, String> topicNameMap() {
        return topicMapper.selectList(null).stream()
                .collect(Collectors.toMap(GeoTopic::getId, GeoTopic::getTopicName, (a, b) -> a));
    }

    private GeoDailyVO toVo(GeoMonitorDaily e, Map<Long, String> topicNames) {
        GeoDailyVO vo = new GeoDailyVO();
        vo.setId(e.getId());
        vo.setInspectDate(e.getInspectDate());
        vo.setPlatform(e.getPlatform());
        vo.setKeyword(e.getKeyword());
        vo.setTopicId(e.getTopicId());
        vo.setTopicName(topicNames.getOrDefault(e.getTopicId(), ""));
        vo.setMentioned(e.getMentioned());
        vo.setRankNo(e.getRankNo());
        vo.setRecommendStatus(e.getRecommendStatus());
        vo.setScreenshotUrl(e.getScreenshotUrl());
        vo.setThirdPartyUrl(e.getThirdPartyUrl());
        vo.setNegativeContent(e.getNegativeContent());
        vo.setCompetitors(e.getCompetitors());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    private List<DateGroup> parseDateGroups(Sheet sheet, int lastCol) {
        List<DateGroup> groups = new ArrayList<>();
        for (int col = DATE_START_COL; col < lastCol; col += COLS_PER_DATE) {
            LocalDate date = ExcelCellUtils.date(sheet, 0, col);
            if (date == null) {
                continue;
            }
            DateGroup group = new DateGroup();
            group.date = date;
            group.startCol = col;
            for (int p = 0; p < PLATFORMS_PER_METRIC; p++) {
                String platform = ExcelCellUtils.str(sheet, 2, col + p);
                if (StringUtils.hasText(platform)) {
                    group.platforms.add(platform);
                }
            }
            if (!group.platforms.isEmpty()) {
                groups.add(group);
            }
        }
        if (groups.isEmpty()) {
            throw new BusinessException("未识别到巡查日期列，请使用与样例一致的宽表格式");
        }
        return groups;
    }

    private static int parseMentioned(String raw) {
        if (raw.contains("✅") || raw.contains("是") || "1".equals(raw)) {
            return 1;
        }
        return 0;
    }

    private static Integer parseRank(String raw) {
        if (!StringUtils.hasText(raw) || "-".equals(raw) || "—".equals(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isHttp(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith("http");
    }

    private static LocalDate[] weekRange(GeoBoardQueryDTO query) {
        LocalDate end = query.getEndDate() != null ? query.getEndDate() : LocalDate.now();
        end = end.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate start = query.getStartDate() != null
                ? query.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : end.minusWeeks(4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LocalDate[]{start, end};
    }

    private static LocalDate[] dayRange(GeoBoardQueryDTO query) {
        LocalDate end = query.getEndDate() != null ? query.getEndDate() : LocalDate.now();
        LocalDate start = query.getStartDate() != null ? query.getStartDate() : end.minusDays(13);
        return new LocalDate[]{start, end};
    }

    private static LocalDate[] yearRange(GeoBoardQueryDTO query) {
        LocalDate end = query.getEndDate() != null ? query.getEndDate() : LocalDate.now();
        end = LocalDate.of(end.getYear(), 12, 31);
        LocalDate start = query.getStartDate() != null
                ? LocalDate.of(query.getStartDate().getYear(), 1, 1)
                : LocalDate.of(end.getYear() - 1, 1, 1);
        return new LocalDate[]{start, end};
    }

    private static boolean overlaps(GeoYearTarget target, LocalDate start, LocalDate end) {
        LocalDate ts = target.getPeriodStart() != null ? target.getPeriodStart() : start;
        LocalDate te = target.getPeriodEnd() != null ? target.getPeriodEnd() : end;
        return !ts.isAfter(end) && !te.isBefore(start);
    }

    private List<TrendRow> aggregateTrend(List<GeoMonitorDaily> records, Function<GeoMonitorDaily, String> axisFn,
                                          Map<Long, String> topicNames) {
        Map<String, List<GeoMonitorDaily>> grouped = records.stream()
                .collect(Collectors.groupingBy(r -> axisFn.apply(r) + "\0" + r.getTopicId() + "\0" + r.getPlatform(),
                        LinkedHashMap::new, Collectors.toList()));
        List<TrendRow> rows = new ArrayList<>();
        for (List<GeoMonitorDaily> list : grouped.values()) {
            GeoMonitorDaily first = list.getFirst();
            int total = list.size();
            int mentioned = (int) list.stream().filter(x -> Objects.equals(x.getMentioned(), 1)).count();
            int firstRank = (int) list.stream().filter(x -> Objects.equals(x.getRankNo(), 1)).count();
            int recommend = (int) list.stream().filter(x -> "出现且推荐".equals(x.getRecommendStatus())).count();
            rows.add(new TrendRow(
                    axisFn.apply(first),
                    topicNames.getOrDefault(first.getTopicId(), ""),
                    first.getPlatform(),
                    total,
                    pct(mentioned, total),
                    pct(firstRank, mentioned),
                    recommend,
                    topN(list.stream().map(GeoMonitorDaily::getCompetitors).toList(), 5),
                    topN(list.stream().map(x -> hostOf(x.getThirdPartyUrl())).toList(), 5)));
        }
        rows.sort(Comparator.comparing(TrendRow::axis).thenComparing(TrendRow::topicName).thenComparing(TrendRow::platform));
        return rows;
    }

    private void fillMetricCharts(List<GeoMonitorDaily> records, Function<GeoMonitorDaily, String> axisFn,
                                  List<GeoChartPointVO> mentionChart, List<GeoChartPointVO> firstMentionChart,
                                  List<GeoChartPointVO> recommendChart) {
        Map<String, List<GeoMonitorDaily>> chartGroup = records.stream()
                .collect(Collectors.groupingBy(r -> axisFn.apply(r) + "\0" + r.getPlatform(),
                        LinkedHashMap::new, Collectors.toList()));
        chartGroup.forEach((key, list) -> {
            String[] parts = key.split("\0", 2);
            int total = list.size();
            int mentioned = (int) list.stream().filter(x -> Objects.equals(x.getMentioned(), 1)).count();
            int firstRank = (int) list.stream().filter(x -> Objects.equals(x.getRankNo(), 1)).count();
            int recommend = (int) list.stream().filter(x -> "出现且推荐".equals(x.getRecommendStatus())).count();
            addChart(mentionChart, parts[0], parts[1], pct(mentioned, total));
            addChart(firstMentionChart, parts[0], parts[1], pct(firstRank, mentioned));
            addChart(recommendChart, parts[0], parts[1], recommend);
        });
        sortChart(mentionChart);
        sortChart(firstMentionChart);
        sortChart(recommendChart);
    }

    private static String weekLabel(GeoMonitorDaily record) {
        return weekLabel(record.getInspectDate());
    }

    private static String weekLabel(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return year + "年第" + week + "周";
    }

    private static double pct(int num, int den) {
        if (den <= 0) {
            return 0;
        }
        return round(num * 100.0 / den);
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static void sortChart(List<GeoChartPointVO> chart) {
        chart.sort(Comparator.comparing(GeoChartPointVO::getAxis).thenComparing(GeoChartPointVO::getSeries));
    }

    private static void addChart(List<GeoChartPointVO> chart, String axis, String series, double value) {
        GeoChartPointVO p = new GeoChartPointVO();
        p.setAxis(axis);
        p.setSeries(series);
        p.setValue(round(value));
        chart.add(p);
    }

    private static String hostOf(String url) {
        if (!isHttp(url)) {
            return "";
        }
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (Exception e) {
            return "";
        }
    }

    private static String topN(List<String> values, int n) {
        Map<String, Integer> count = new HashMap<>();
        for (String raw : values) {
            if (!StringUtils.hasText(raw) || "无".equals(raw) || "-".equals(raw)) {
                continue;
            }
            for (String part : raw.split("[,，、;/]")) {
                String item = part.trim();
                if (StringUtils.hasText(item)) {
                    count.merge(item, 1, Integer::sum);
                }
            }
        }
        return count.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(Collectors.joining("、"));
    }

    private static class DateGroup {
        private LocalDate date;
        private int startCol;
        private final List<String> platforms = new ArrayList<>();
    }

    private record TrendRow(String axis, String topicName, String platform, int sampleCount, double mentionRate,
                            double firstMentionRate, int recommendCount, String competitorTop, String citePlatformTop) {
    }
}
