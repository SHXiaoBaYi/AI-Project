package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoBoardQueryDTO;
import com.base.admin.domain.dto.GeoDailyBatchDTO;
import com.base.admin.domain.dto.GeoDailyBulkGroupDTO;
import com.base.admin.domain.dto.GeoDailyBulkSaveDTO;
import com.base.admin.domain.dto.GeoDailyDTO;
import com.base.admin.domain.dto.GeoDailyPlatformItemDTO;
import com.base.admin.domain.dto.GeoDailyQueryDTO;
import com.base.admin.domain.dto.GeoYearTargetDTO;
import com.base.admin.domain.entity.GeoBoardPeriodStat;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.GeoYearTarget;
import com.base.admin.domain.enums.GeoPeriodType;
import com.base.admin.domain.vo.GeoChartPointVO;
import com.base.admin.domain.vo.GeoDailyBoardVO;
import com.base.admin.domain.vo.GeoDailyBulkConflictVO;
import com.base.admin.domain.vo.GeoDailyBulkSaveResultVO;
import com.base.admin.domain.vo.GeoDailyGroupVO;
import com.base.admin.domain.vo.GeoDailyVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoLatestDateVO;
import com.base.admin.domain.vo.GeoMonthlyBoardVO;
import com.base.admin.domain.vo.GeoPersistResultVO;
import com.base.admin.domain.vo.GeoWeeklyBoardVO;
import com.base.admin.domain.vo.GeoYearlyBoardVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoBoardPeriodStatMapper;
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
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final GeoBoardPeriodStatMapper periodStatMapper;
    private final GeoTopicService topicService;
    private final GeoPlatformService platformService;

    @Override
    public PageResult<GeoDailyVO> listDaily(GeoDailyQueryDTO query) {
        LambdaQueryWrapper<GeoMonitorDaily> wrapper = buildListDailyWrapper(query);
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
        GeoMonitorDaily entity = requireDaily(id);
        assertDailyMutable(entity.getInspectDate(), entity);
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
    @Transactional
    public GeoDailyBulkSaveResultVO saveDailyBulk(GeoDailyBulkSaveDTO dto) {
        if (dto.getGroups() == null || dto.getGroups().isEmpty()) {
            throw new BusinessException("请至少提交一个话题分组");
        }
        boolean ignoreLocked = Boolean.TRUE.equals(dto.getIgnoreLocked());
        Map<Long, String> topicNames = topicNameMap();
        Set<String> seenKeys = new HashSet<>();
        List<GeoDailyBulkConflictVO> conflicts = new ArrayList<>();
        List<GeoDailyDTO> toInsert = new ArrayList<>();
        List<GeoDailyDTO> toUpdate = new ArrayList<>();

        for (GeoDailyBulkGroupDTO group : dto.getGroups()) {
            if (group == null) {
                continue;
            }
            if (group.getInspectDate() == null) {
                throw new BusinessException("巡查日期不能为空");
            }
            if (group.getTopicId() == null) {
                throw new BusinessException("话题不能为空");
            }
            if (!StringUtils.hasText(group.getKeyword())) {
                throw new BusinessException("关键字不能为空");
            }
            topicService.getById(group.getTopicId());
            String keyword = group.getKeyword().trim();
            String topicName = topicNames.getOrDefault(group.getTopicId(), "");
            boolean datePeriodLocked = isInspectDatePeriodLocked(group.getInspectDate());
            int platformCount = 0;

            for (GeoDailyPlatformItemDTO item : group.getItems()) {
                if (item == null || !StringUtils.hasText(item.getPlatform())) {
                    continue;
                }
                platformCount++;
                String platform = item.getPlatform().trim();
                String uk = group.getInspectDate() + "|" + platform + "|" + keyword;
                if (!seenKeys.add(uk)) {
                    throw new BusinessException("提交数据存在重复：" + group.getInspectDate()
                            + " / " + platform + " / 「" + keyword + "」");
                }

                GeoDailyDTO one = new GeoDailyDTO();
                one.setId(item.getId());
                one.setInspectDate(group.getInspectDate());
                one.setTopicId(group.getTopicId());
                one.setKeyword(keyword);
                one.setPlatform(platform);
                one.setMentioned(item.getMentioned());
                one.setRankNo(item.getRankNo());
                one.setRecommendStatus(item.getRecommendStatus());
                one.setScreenshotUrl(item.getScreenshotUrl());
                one.setThirdPartyUrl(item.getThirdPartyUrl());
                one.setNegativeContent(item.getNegativeContent());
                one.setCompetitors(item.getCompetitors());

                GeoMonitorDaily existing = dailyMapper.selectUkIncludeDeleted(
                        group.getInspectDate(), platform, keyword);
                if (existing != null && Integer.valueOf(1).equals(existing.getBoardLocked())) {
                    conflicts.add(buildConflict(existing, topicName,
                            "该记录已被周/月/年统计，不可覆盖"));
                    continue;
                }
                if (existing == null && datePeriodLocked) {
                    GeoDailyBulkConflictVO conflict = new GeoDailyBulkConflictVO();
                    conflict.setInspectDate(group.getInspectDate());
                    conflict.setPlatform(platform);
                    conflict.setKeyword(keyword);
                    conflict.setTopicId(group.getTopicId());
                    conflict.setTopicName(topicName);
                    conflict.setReason("巡查日期 " + group.getInspectDate() + " 已被周/月/年统计，不可新增");
                    conflicts.add(conflict);
                    continue;
                }
                if (existing == null) {
                    toInsert.add(one);
                } else {
                    one.setId(existing.getId());
                    toUpdate.add(one);
                }
            }
            if (platformCount == 0) {
                throw new BusinessException("话题「" + topicName + "」请至少填写一个平台");
            }
        }

        GeoDailyBulkSaveResultVO result = new GeoDailyBulkSaveResultVO();
        result.setLockedConflicts(conflicts);

        if (!ignoreLocked && !conflicts.isEmpty()) {
            result.setNeedConfirm(true);
            return result;
        }

        if (ignoreLocked) {
            result.setSkippedLockedCount(conflicts.size());
        }

        if (toInsert.isEmpty() && toUpdate.isEmpty()) {
            if (!conflicts.isEmpty() && ignoreLocked) {
                result.setNeedConfirm(false);
                return result;
            }
            throw new BusinessException("没有可保存的监测数据");
        }

        int insertCount = 0;
        int updateCount = 0;
        for (GeoDailyDTO one : toInsert) {
            upsertDailyAllowOverwriteUnlocked(one, true);
            insertCount++;
        }
        for (GeoDailyDTO one : toUpdate) {
            upsertDailyAllowOverwriteUnlocked(one, false);
            updateCount++;
        }

        result.setNeedConfirm(false);
        result.setInsertCount(insertCount);
        result.setUpdateCount(updateCount);
        return result;
    }

    private GeoDailyBulkConflictVO buildConflict(GeoMonitorDaily existing, String topicName, String reason) {
        GeoDailyBulkConflictVO conflict = new GeoDailyBulkConflictVO();
        conflict.setId(existing.getId());
        conflict.setInspectDate(existing.getInspectDate());
        conflict.setPlatform(existing.getPlatform());
        conflict.setKeyword(existing.getKeyword());
        conflict.setTopicId(existing.getTopicId());
        conflict.setTopicName(topicName);
        conflict.setReason(reason);
        return conflict;
    }

    private boolean isInspectDatePeriodLocked(LocalDate inspectDate) {
        if (inspectDate == null) {
            return false;
        }
        Long locked = periodStatMapper.selectCount(new LambdaQueryWrapper<GeoBoardPeriodStat>()
                .le(GeoBoardPeriodStat::getPeriodStart, inspectDate)
                .ge(GeoBoardPeriodStat::getPeriodEnd, inspectDate)
                .last("LIMIT 1"));
        return locked != null && locked > 0;
    }

    /**
     * 批量新增专用：已存在且未统计则覆盖；不做“整日周期锁定”拦截（冲突已在上层识别）。
     */
    private void upsertDailyAllowOverwriteUnlocked(GeoDailyDTO dto, boolean allowInsert) {
        topicService.getById(dto.getTopicId());
        platformService.getOrCreate(dto.getPlatform());
        String platform = dto.getPlatform().trim();
        String keyword = dto.getKeyword().trim();
        GeoMonitorDaily existing = dailyMapper.selectUkIncludeDeleted(dto.getInspectDate(), platform, keyword);
        if (existing == null && dto.getId() != null) {
            existing = dailyMapper.selectById(dto.getId());
        }
        if (existing != null && Integer.valueOf(1).equals(existing.getBoardLocked())) {
            throw new BusinessException("该日监测数据已被周/月/年统计，不可编辑或删除");
        }
        if (existing == null) {
            if (!allowInsert) {
                throw new BusinessException("监测数据不存在");
            }
            GeoMonitorDaily entity = new GeoMonitorDaily();
            fillDaily(entity, dto);
            entity.setBoardLocked(0);
            dailyMapper.insert(entity);
            return;
        }
        if (existing.getIsActive() == null || existing.getIsActive() == 0) {
            dailyMapper.restoreActive(existing.getId());
            existing.setIsActive(1);
        }
        fillDaily(existing, dto);
        dailyMapper.updateById(existing);
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
        List<TrendAgg> live = liveTrend(range[0], range[1], query, GeoMonitorServiceImpl::weekKey,
                GeoMonitorServiceImpl::weekLabel, GeoMonitorServiceImpl::weekBounds);
        return buildWeeklyBoard(range, query, live);
    }

    @Override
    public GeoMonthlyBoardVO monthlyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = monthRange(query);
        List<TrendAgg> live = liveTrend(range[0], range[1], query, GeoMonitorServiceImpl::monthKey,
                GeoMonitorServiceImpl::monthLabel, GeoMonitorServiceImpl::monthBounds);
        return buildMonthlyBoard(range, query, live);
    }

    @Override
    public GeoDailyBoardVO dailyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = dayRange(query);
        List<GeoMonitorDaily> records = dailyMapper.selectList(
                buildDailyWrapper(range[0], range[1], query.getTopicId(), query.getKeyword(), query.getPlatforms()));
        Map<Long, String> topicNames = topicNameMap();
        GeoDailyBoardVO board = new GeoDailyBoardVO();
        for (TrendAgg row : aggregateTrend(records, r -> r.getInspectDate().toString(),
                r -> r.getInspectDate().toString(), r -> new LocalDate[]{r.getInspectDate(), r.getInspectDate()}, topicNames)) {
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
        List<YearAgg> live = liveYearly(range[0], range[1], query);
        return buildYearlyBoard(range, query, live);
    }

    @Override
    @Transactional
    public GeoPersistResultVO persistWeeklyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = weekRange(query);
        List<TrendAgg> live = liveTrend(range[0], range[1], query, GeoMonitorServiceImpl::weekKey,
                GeoMonitorServiceImpl::weekLabel, GeoMonitorServiceImpl::weekBounds);
        return persistTrend(GeoPeriodType.WEEK, live, true);
    }

    @Override
    @Transactional
    public GeoPersistResultVO persistMonthlyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = monthRange(query);
        List<TrendAgg> live = liveTrend(range[0], range[1], query, GeoMonitorServiceImpl::monthKey,
                GeoMonitorServiceImpl::monthLabel, GeoMonitorServiceImpl::monthBounds);
        return persistTrend(GeoPeriodType.MONTH, live, true);
    }

    @Override
    @Transactional
    public GeoPersistResultVO persistYearlyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = yearRange(query);
        List<YearAgg> live = liveYearly(range[0], range[1], query);
        return persistYearly(live, true);
    }

    @Override
    @Transactional
    public GeoPersistResultVO autoPersistCompletedWeekly(int lookbackWeeks) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        int weeks = Math.max(lookbackWeeks, 1);
        LocalDate start = end.minusWeeks(weeks - 1L).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<TrendAgg> live = liveTrend(start, end, new GeoBoardQueryDTO(), GeoMonitorServiceImpl::weekKey,
                GeoMonitorServiceImpl::weekLabel, GeoMonitorServiceImpl::weekBounds).stream()
                .filter(r -> r.periodEnd.isBefore(today))
                .toList();
        return persistTrend(GeoPeriodType.WEEK, live, false);
    }

    @Override
    @Transactional
    public GeoPersistResultVO autoPersistCompletedMonthly(int lookbackMonths) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.with(TemporalAdjusters.firstDayOfMonth()).minusDays(1);
        int months = Math.max(lookbackMonths, 1);
        LocalDate start = end.minusMonths(months - 1L).with(TemporalAdjusters.firstDayOfMonth());
        List<TrendAgg> live = liveTrend(start, end, new GeoBoardQueryDTO(), GeoMonitorServiceImpl::monthKey,
                GeoMonitorServiceImpl::monthLabel, GeoMonitorServiceImpl::monthBounds).stream()
                .filter(r -> r.periodEnd.isBefore(today))
                .toList();
        return persistTrend(GeoPeriodType.MONTH, live, false);
    }

    @Override
    @Transactional
    public GeoPersistResultVO autoPersistCompletedYearly(int lookbackYears) {
        LocalDate today = LocalDate.now();
        int years = Math.max(lookbackYears, 1);
        LocalDate end = LocalDate.of(today.getYear(), 12, 31);
        LocalDate start = LocalDate.of(today.getYear() - years + 1, 1, 1);
        List<YearAgg> live = liveYearly(start, end, new GeoBoardQueryDTO()).stream()
                .filter(r -> r.periodEnd.isBefore(today))
                .toList();
        return persistYearly(live, false);
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

    private List<TrendAgg> liveTrend(LocalDate start, LocalDate end, GeoBoardQueryDTO query,
                                     Function<GeoMonitorDaily, String> keyFn,
                                     Function<GeoMonitorDaily, String> labelFn,
                                     Function<GeoMonitorDaily, LocalDate[]> boundsFn) {
        List<GeoMonitorDaily> records = dailyMapper.selectList(
                buildDailyWrapper(start, end, query.getTopicId(), query.getKeyword(), query.getPlatforms()));
        return aggregateTrend(records, keyFn, labelFn, boundsFn, topicNameMap());
    }

    private GeoWeeklyBoardVO buildWeeklyBoard(LocalDate[] range, GeoBoardQueryDTO query, List<TrendAgg> live) {
        Map<String, TrendAgg> merged = mergeTrendWithSnapshots(GeoPeriodType.WEEK, range, query, live);
        GeoWeeklyBoardVO board = new GeoWeeklyBoardVO();
        Set<String> persistedKeys = new HashSet<>();
        for (TrendAgg row : merged.values()) {
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
            vo.setFromSnapshot(row.fromSnapshot);
            board.getRows().add(vo);
            if (row.fromSnapshot) {
                persistedKeys.add(row.periodKey);
            }
        }
        board.getRows().sort(Comparator.comparing(GeoWeeklyBoardVO.GeoWeeklyRowVO::getWeekLabel)
                .thenComparing(GeoWeeklyBoardVO.GeoWeeklyRowVO::getTopicName)
                .thenComparing(GeoWeeklyBoardVO.GeoWeeklyRowVO::getPlatform));
        fillChartsFromTrend(merged.values(), board.getMentionChart(), board.getFirstMentionChart(), board.getRecommendChart());
        board.setPersistedPeriodCount(persistedKeys.size());
        return board;
    }

    private GeoMonthlyBoardVO buildMonthlyBoard(LocalDate[] range, GeoBoardQueryDTO query, List<TrendAgg> live) {
        Map<String, TrendAgg> merged = mergeTrendWithSnapshots(GeoPeriodType.MONTH, range, query, live);
        GeoMonthlyBoardVO board = new GeoMonthlyBoardVO();
        Set<String> persistedKeys = new HashSet<>();
        for (TrendAgg row : merged.values()) {
            GeoMonthlyBoardVO.GeoMonthlyRowVO vo = new GeoMonthlyBoardVO.GeoMonthlyRowVO();
            vo.setMonthLabel(row.axis);
            vo.setTopicName(row.topicName);
            vo.setPlatform(row.platform);
            vo.setSampleCount(row.sampleCount);
            vo.setMentionRate(row.mentionRate);
            vo.setFirstMentionRate(row.firstMentionRate);
            vo.setRecommendCount(row.recommendCount);
            vo.setCompetitorTop(row.competitorTop);
            vo.setCitePlatformTop(row.citePlatformTop);
            vo.setFromSnapshot(row.fromSnapshot);
            board.getRows().add(vo);
            if (row.fromSnapshot) {
                persistedKeys.add(row.periodKey);
            }
        }
        board.getRows().sort(Comparator.comparing(GeoMonthlyBoardVO.GeoMonthlyRowVO::getMonthLabel)
                .thenComparing(GeoMonthlyBoardVO.GeoMonthlyRowVO::getTopicName)
                .thenComparing(GeoMonthlyBoardVO.GeoMonthlyRowVO::getPlatform));
        fillChartsFromTrend(merged.values(), board.getMentionChart(), board.getFirstMentionChart(), board.getRecommendChart());
        board.setPersistedPeriodCount(persistedKeys.size());
        return board;
    }

    private GeoYearlyBoardVO buildYearlyBoard(LocalDate[] range, GeoBoardQueryDTO query, List<YearAgg> live) {
        Map<String, YearAgg> merged = mergeYearlyWithSnapshots(range, query, live);
        GeoYearlyBoardVO board = new GeoYearlyBoardVO();
        Set<String> persistedKeys = new HashSet<>();
        for (YearAgg row : merged.values()) {
            GeoYearlyBoardVO.GeoYearlyRowVO vo = new GeoYearlyBoardVO.GeoYearlyRowVO();
            vo.setPeriodLabel(row.periodLabel);
            vo.setTopicName(row.topicName);
            vo.setPlatform(row.platform);
            vo.setTargetRate(row.targetRate);
            vo.setActualRate(row.actualRate);
            vo.setAchieveRate(row.achieveRate);
            vo.setSampleCount(row.sampleCount);
            vo.setFromSnapshot(row.fromSnapshot);
            board.getRows().add(vo);
            addChart(board.getActualChart(), row.periodLabel + " / " + row.topicName, row.platform, row.actualRate);
            addChart(board.getAchieveChart(), row.periodLabel + " / " + row.topicName, row.platform, row.achieveRate);
            if (row.fromSnapshot) {
                persistedKeys.add(row.periodKey);
            }
        }
        board.getRows().sort(Comparator.comparing(GeoYearlyBoardVO.GeoYearlyRowVO::getPeriodLabel)
                .thenComparing(GeoYearlyBoardVO.GeoYearlyRowVO::getTopicName)
                .thenComparing(GeoYearlyBoardVO.GeoYearlyRowVO::getPlatform));
        sortChart(board.getActualChart());
        sortChart(board.getAchieveChart());
        board.setPersistedPeriodCount(persistedKeys.size());
        return board;
    }

    private Map<String, TrendAgg> mergeTrendWithSnapshots(GeoPeriodType type, LocalDate[] range,
                                                          GeoBoardQueryDTO query, List<TrendAgg> live) {
        Map<String, TrendAgg> merged = new LinkedHashMap<>();
        for (TrendAgg row : live) {
            merged.put(rowKey(row.periodKey, row.topicId, row.platform), row);
        }
        // 快照按 topic+platform 粒度落库，关键字筛选时仅展示实时聚合，避免口径不一致
        if (!StringUtils.hasText(query.getKeyword())) {
            for (GeoBoardPeriodStat snap : listSnapshots(type, range[0], range[1], query)) {
                TrendAgg row = TrendAgg.fromSnapshot(snap);
                merged.put(rowKey(row.periodKey, row.topicId, row.platform), row);
            }
        }
        return merged;
    }

    private Map<String, YearAgg> mergeYearlyWithSnapshots(LocalDate[] range, GeoBoardQueryDTO query, List<YearAgg> live) {
        Map<String, YearAgg> merged = new LinkedHashMap<>();
        for (YearAgg row : live) {
            merged.put(rowKey(row.periodKey, row.topicId, row.platform), row);
        }
        if (!StringUtils.hasText(query.getKeyword())) {
            for (GeoBoardPeriodStat snap : listSnapshots(GeoPeriodType.YEAR, range[0], range[1], query)) {
                YearAgg row = YearAgg.fromSnapshot(snap);
                merged.put(rowKey(row.periodKey, row.topicId, row.platform), row);
            }
        }
        return merged;
    }

    private List<GeoBoardPeriodStat> listSnapshots(GeoPeriodType type, LocalDate start, LocalDate end, GeoBoardQueryDTO query) {
        List<String> platformFilter = query.getPlatforms() == null ? List.of()
                : query.getPlatforms().stream().filter(StringUtils::hasText).toList();
        return periodStatMapper.selectList(new LambdaQueryWrapper<GeoBoardPeriodStat>()
                .eq(GeoBoardPeriodStat::getPeriodType, type.getCode())
                .le(GeoBoardPeriodStat::getPeriodStart, end)
                .ge(GeoBoardPeriodStat::getPeriodEnd, start)
                .eq(query.getTopicId() != null, GeoBoardPeriodStat::getTopicId, query.getTopicId())
                .in(!platformFilter.isEmpty(), GeoBoardPeriodStat::getPlatform, platformFilter));
    }

    private GeoPersistResultVO persistTrend(GeoPeriodType type, List<TrendAgg> rows, boolean requireData) {
        if (rows.isEmpty()) {
            if (requireData) {
                throw new BusinessException("当前筛选范围内没有可落库的日监测数据");
            }
            return emptyPersistResult();
        }
        LocalDateTime now = LocalDateTime.now();
        int snapshotCount = 0;
        Set<String> periodKeys = new HashSet<>();
        Map<String, LocalDate[]> bounds = new LinkedHashMap<>();
        for (TrendAgg row : rows) {
            upsertTrendSnapshot(type, row, now);
            snapshotCount++;
            periodKeys.add(row.periodKey);
            bounds.putIfAbsent(row.periodKey, new LocalDate[]{row.periodStart, row.periodEnd});
        }
        int locked = lockPeriods(bounds.values());
        GeoPersistResultVO result = new GeoPersistResultVO();
        result.setSnapshotCount(snapshotCount);
        result.setLockedDailyCount(locked);
        result.setPeriodCount(periodKeys.size());
        return result;
    }

    private GeoPersistResultVO persistYearly(List<YearAgg> rows, boolean requireData) {
        if (rows.isEmpty()) {
            if (requireData) {
                throw new BusinessException("当前筛选范围内没有可落库的年度数据");
            }
            return emptyPersistResult();
        }
        LocalDateTime now = LocalDateTime.now();
        int snapshotCount = 0;
        Set<String> periodKeys = new HashSet<>();
        Map<String, LocalDate[]> bounds = new LinkedHashMap<>();
        for (YearAgg row : rows) {
            upsertYearSnapshot(row, now);
            snapshotCount++;
            periodKeys.add(row.periodKey);
            bounds.putIfAbsent(row.periodKey, new LocalDate[]{row.periodStart, row.periodEnd});
        }
        int locked = lockPeriods(bounds.values());
        GeoPersistResultVO result = new GeoPersistResultVO();
        result.setSnapshotCount(snapshotCount);
        result.setLockedDailyCount(locked);
        result.setPeriodCount(periodKeys.size());
        return result;
    }

    private static GeoPersistResultVO emptyPersistResult() {
        GeoPersistResultVO result = new GeoPersistResultVO();
        result.setSnapshotCount(0);
        result.setLockedDailyCount(0);
        result.setPeriodCount(0);
        return result;
    }

    private int lockPeriods(Iterable<LocalDate[]> boundsList) {
        int locked = 0;
        for (LocalDate[] bounds : boundsList) {
            locked += dailyMapper.lockBoardRange(bounds[0], bounds[1]);
        }
        return locked;
    }

    private void upsertTrendSnapshot(GeoPeriodType type, TrendAgg row, LocalDateTime now) {
        GeoBoardPeriodStat existing = periodStatMapper.selectOne(new LambdaQueryWrapper<GeoBoardPeriodStat>()
                .eq(GeoBoardPeriodStat::getPeriodType, type.getCode())
                .eq(GeoBoardPeriodStat::getPeriodKey, row.periodKey)
                .eq(GeoBoardPeriodStat::getTopicId, row.topicId)
                .eq(GeoBoardPeriodStat::getPlatform, row.platform)
                .last("LIMIT 1"));
        GeoBoardPeriodStat entity = existing != null ? existing : new GeoBoardPeriodStat();
        entity.setPeriodType(type.getCode());
        entity.setPeriodKey(row.periodKey);
        entity.setPeriodLabel(row.axis);
        entity.setPeriodStart(row.periodStart);
        entity.setPeriodEnd(row.periodEnd);
        entity.setTopicId(row.topicId);
        entity.setTopicName(row.topicName);
        entity.setPlatform(row.platform);
        entity.setSampleCount(row.sampleCount);
        entity.setMentionRate(BigDecimal.valueOf(row.mentionRate));
        entity.setFirstMentionRate(BigDecimal.valueOf(row.firstMentionRate));
        entity.setRecommendCount(row.recommendCount);
        entity.setCompetitorTop(row.competitorTop);
        entity.setCitePlatformTop(row.citePlatformTop);
        entity.setTargetRate(null);
        entity.setActualRate(null);
        entity.setAchieveRate(null);
        entity.setLockedAt(now);
        if (existing == null) {
            periodStatMapper.insert(entity);
        } else {
            periodStatMapper.updateById(entity);
        }
    }

    private void upsertYearSnapshot(YearAgg row, LocalDateTime now) {
        GeoBoardPeriodStat existing = periodStatMapper.selectOne(new LambdaQueryWrapper<GeoBoardPeriodStat>()
                .eq(GeoBoardPeriodStat::getPeriodType, GeoPeriodType.YEAR.getCode())
                .eq(GeoBoardPeriodStat::getPeriodKey, row.periodKey)
                .eq(GeoBoardPeriodStat::getTopicId, row.topicId)
                .eq(GeoBoardPeriodStat::getPlatform, row.platform)
                .last("LIMIT 1"));
        GeoBoardPeriodStat entity = existing != null ? existing : new GeoBoardPeriodStat();
        entity.setPeriodType(GeoPeriodType.YEAR.getCode());
        entity.setPeriodKey(row.periodKey);
        entity.setPeriodLabel(row.periodLabel);
        entity.setPeriodStart(row.periodStart);
        entity.setPeriodEnd(row.periodEnd);
        entity.setTopicId(row.topicId);
        entity.setTopicName(row.topicName);
        entity.setPlatform(row.platform);
        entity.setSampleCount(row.sampleCount);
        entity.setMentionRate(BigDecimal.valueOf(row.actualRate));
        entity.setFirstMentionRate(BigDecimal.ZERO);
        entity.setRecommendCount(0);
        entity.setCompetitorTop("");
        entity.setCitePlatformTop("");
        entity.setTargetRate(row.targetRate);
        entity.setActualRate(BigDecimal.valueOf(row.actualRate));
        entity.setAchieveRate(BigDecimal.valueOf(row.achieveRate));
        entity.setLockedAt(now);
        if (existing == null) {
            periodStatMapper.insert(entity);
        } else {
            periodStatMapper.updateById(entity);
        }
    }

    private List<YearAgg> liveYearly(LocalDate start, LocalDate end, GeoBoardQueryDTO query) {
        List<GeoMonitorDaily> records = dailyMapper.selectList(
                buildDailyWrapper(start, end, query.getTopicId(), query.getKeyword(), query.getPlatforms()));
        Map<Long, String> topicNames = topicNameMap();
        List<GeoYearTarget> targets = targetMapper.selectList(new LambdaQueryWrapper<GeoYearTarget>()
                .eq(query.getTopicId() != null, GeoYearTarget::getTopicId, query.getTopicId())
                .orderByAsc(GeoYearTarget::getSortOrder));
        List<YearAgg> rows = new ArrayList<>();
        if (!targets.isEmpty()) {
            for (GeoYearTarget target : targets) {
                if (!overlaps(target, start, end)) {
                    continue;
                }
                LocalDate ts = target.getPeriodStart() != null ? target.getPeriodStart() : start;
                LocalDate te = target.getPeriodEnd() != null ? target.getPeriodEnd() : end;
                String periodKey = yearPeriodKey(target.getPeriodLabel(), ts, te);
                List<GeoMonitorDaily> scoped = records.stream()
                        .filter(r -> r.getTopicId().equals(target.getTopicId()))
                        .filter(r -> !r.getInspectDate().isBefore(ts) && !r.getInspectDate().isAfter(te))
                        .toList();
                Map<String, List<GeoMonitorDaily>> byPlatform = scoped.stream()
                        .collect(Collectors.groupingBy(GeoMonitorDaily::getPlatform));
                String topicName = topicNames.getOrDefault(target.getTopicId(), "");
                if (byPlatform.isEmpty()) {
                    rows.add(YearAgg.live(periodKey, target.getPeriodLabel(), target.getTopicId(), topicName,
                            "-", ts, te, target.getTargetRate(), List.of()));
                } else {
                    byPlatform.forEach((platform, list) -> rows.add(YearAgg.live(
                            periodKey, target.getPeriodLabel(), target.getTopicId(), topicName,
                            platform, ts, te, target.getTargetRate(), list)));
                }
            }
        } else {
            Map<String, List<GeoMonitorDaily>> grouped = records.stream()
                    .collect(Collectors.groupingBy(r -> r.getInspectDate().getYear() + "\0" + r.getTopicId() + "\0" + r.getPlatform()));
            grouped.forEach((key, list) -> {
                GeoMonitorDaily first = list.getFirst();
                int year = first.getInspectDate().getYear();
                LocalDate ts = LocalDate.of(year, 1, 1);
                LocalDate te = LocalDate.of(year, 12, 31);
                String label = String.valueOf(year);
                rows.add(YearAgg.live(yearPeriodKey(label, ts, te), label, first.getTopicId(),
                        topicNames.getOrDefault(first.getTopicId(), ""), first.getPlatform(),
                        ts, te, new BigDecimal("80.00"), list));
            });
        }
        return rows;
    }

    private boolean upsertDaily(GeoDailyDTO dto, boolean allowInsert) {
        topicService.getById(dto.getTopicId());
        platformService.getOrCreate(dto.getPlatform());
        String platform = dto.getPlatform().trim();
        String keyword = dto.getKeyword().trim();
        assertDailyMutable(dto.getInspectDate(), null);
        GeoMonitorDaily existing = dailyMapper.selectUkIncludeDeleted(dto.getInspectDate(), platform, keyword);
        if (existing == null && dto.getId() != null) {
            existing = dailyMapper.selectById(dto.getId());
            if (existing == null) {
                existing = requireDaily(dto.getId());
            }
        }
        if (existing != null) {
            assertDailyMutable(existing.getInspectDate(), existing);
            if (dto.getInspectDate() != null && !dto.getInspectDate().equals(existing.getInspectDate())) {
                assertDailyMutable(dto.getInspectDate(), null);
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
            entity.setBoardLocked(0);
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

    private void assertDailyMutable(LocalDate inspectDate, GeoMonitorDaily entity) {
        if (entity != null && Integer.valueOf(1).equals(entity.getBoardLocked())) {
            throw new BusinessException("该日监测数据已被周/月/年统计，不可编辑或删除");
        }
        if (inspectDate == null) {
            return;
        }
        Long locked = periodStatMapper.selectCount(new LambdaQueryWrapper<GeoBoardPeriodStat>()
                .le(GeoBoardPeriodStat::getPeriodStart, inspectDate)
                .ge(GeoBoardPeriodStat::getPeriodEnd, inspectDate)
                .last("LIMIT 1"));
        if (locked != null && locked > 0) {
            throw new BusinessException("巡查日期 " + inspectDate + " 已被周/月/年统计，不可新增或修改日监测数据");
        }
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

    private LambdaQueryWrapper<GeoMonitorDaily> buildListDailyWrapper(GeoDailyQueryDTO query) {
        List<String> platformFilter = query.getPlatforms() == null ? List.of()
                : query.getPlatforms().stream().filter(StringUtils::hasText).toList();
        LambdaQueryWrapper<GeoMonitorDaily> wrapper = new LambdaQueryWrapper<GeoMonitorDaily>()
                .ge(query.getStartDate() != null, GeoMonitorDaily::getInspectDate, query.getStartDate())
                .le(query.getEndDate() != null, GeoMonitorDaily::getInspectDate, query.getEndDate())
                .eq(query.getTopicId() != null, GeoMonitorDaily::getTopicId, query.getTopicId())
                .like(StringUtils.hasText(query.getKeyword()), GeoMonitorDaily::getKeyword, query.getKeyword())
                .in(!platformFilter.isEmpty(), GeoMonitorDaily::getPlatform, platformFilter)
                .eq(query.getMentioned() != null, GeoMonitorDaily::getMentioned, query.getMentioned())
                .ge(query.getRankNoMin() != null, GeoMonitorDaily::getRankNo, query.getRankNoMin())
                .le(query.getRankNoMax() != null, GeoMonitorDaily::getRankNo, query.getRankNoMax())
                .eq(StringUtils.hasText(query.getRecommendStatus()), GeoMonitorDaily::getRecommendStatus, query.getRecommendStatus())
                .like(StringUtils.hasText(query.getCompetitors()), GeoMonitorDaily::getCompetitors, query.getCompetitors())
                .eq(query.getBoardLocked() != null, GeoMonitorDaily::getBoardLocked, query.getBoardLocked())
                .ge(query.getUpdateTimeStart() != null, GeoMonitorDaily::getUpdateTime, query.getUpdateTimeStart())
                .le(query.getUpdateTimeEnd() != null, GeoMonitorDaily::getUpdateTime, query.getUpdateTimeEnd());
        applyHasTextFilter(wrapper, query.getHasScreenshot(), GeoMonitorDaily::getScreenshotUrl);
        applyHasTextFilter(wrapper, query.getHasThirdPartyUrl(), GeoMonitorDaily::getThirdPartyUrl);
        return wrapper;
    }

    private void applyHasTextFilter(LambdaQueryWrapper<GeoMonitorDaily> wrapper, Integer hasValue,
                                    com.baomidou.mybatisplus.core.toolkit.support.SFunction<GeoMonitorDaily, ?> column) {
        if (hasValue == null) {
            return;
        }
        if (Integer.valueOf(1).equals(hasValue)) {
            wrapper.isNotNull(column).ne(column, "");
            return;
        }
        if (Integer.valueOf(0).equals(hasValue)) {
            wrapper.and(w -> w.isNull(column).or().eq(column, ""));
        }
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
        vo.setBoardLocked(e.getBoardLocked() == null ? 0 : e.getBoardLocked());
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

    private static LocalDate[] monthRange(GeoBoardQueryDTO query) {
        LocalDate end = query.getEndDate() != null ? query.getEndDate() : LocalDate.now();
        end = end.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate start = query.getStartDate() != null
                ? query.getStartDate().with(TemporalAdjusters.firstDayOfMonth())
                : end.minusMonths(5).with(TemporalAdjusters.firstDayOfMonth());
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

    private List<TrendAgg> aggregateTrend(List<GeoMonitorDaily> records,
                                          Function<GeoMonitorDaily, String> keyFn,
                                          Function<GeoMonitorDaily, String> labelFn,
                                          Function<GeoMonitorDaily, LocalDate[]> boundsFn,
                                          Map<Long, String> topicNames) {
        Map<String, List<GeoMonitorDaily>> grouped = records.stream()
                .collect(Collectors.groupingBy(r -> keyFn.apply(r) + "\0" + r.getTopicId() + "\0" + r.getPlatform(),
                        LinkedHashMap::new, Collectors.toList()));
        List<TrendAgg> rows = new ArrayList<>();
        for (List<GeoMonitorDaily> list : grouped.values()) {
            GeoMonitorDaily first = list.getFirst();
            int total = list.size();
            int mentioned = (int) list.stream().filter(x -> Objects.equals(x.getMentioned(), 1)).count();
            int firstRank = (int) list.stream().filter(x -> Objects.equals(x.getRankNo(), 1)).count();
            int recommend = (int) list.stream().filter(x -> "出现且推荐".equals(x.getRecommendStatus())).count();
            LocalDate[] bounds = boundsFn.apply(first);
            rows.add(new TrendAgg(
                    keyFn.apply(first),
                    labelFn.apply(first),
                    first.getTopicId(),
                    topicNames.getOrDefault(first.getTopicId(), ""),
                    first.getPlatform(),
                    bounds[0],
                    bounds[1],
                    total,
                    pct(mentioned, total),
                    pct(firstRank, mentioned),
                    recommend,
                    topN(list.stream().map(GeoMonitorDaily::getCompetitors).toList(), 5),
                    topN(list.stream().map(x -> hostOf(x.getThirdPartyUrl())).toList(), 5),
                    false));
        }
        rows.sort(Comparator.comparing(TrendAgg::axis).thenComparing(TrendAgg::topicName).thenComparing(TrendAgg::platform));
        return rows;
    }

    private void fillChartsFromTrend(Iterable<TrendAgg> rows, List<GeoChartPointVO> mentionChart,
                                     List<GeoChartPointVO> firstMentionChart, List<GeoChartPointVO> recommendChart) {
        Map<String, List<TrendAgg>> grouped = new LinkedHashMap<>();
        for (TrendAgg row : rows) {
            grouped.computeIfAbsent(row.axis + "\0" + row.platform, k -> new ArrayList<>()).add(row);
        }
        grouped.forEach((key, list) -> {
            String[] parts = key.split("\0", 2);
            double mention = weightedAvg(list, r -> r.mentionRate);
            double firstMention = weightedAvg(list, r -> r.firstMentionRate);
            int recommend = list.stream().mapToInt(r -> r.recommendCount).sum();
            addChart(mentionChart, parts[0], parts[1], mention);
            addChart(firstMentionChart, parts[0], parts[1], firstMention);
            addChart(recommendChart, parts[0], parts[1], recommend);
        });
        sortChart(mentionChart);
        sortChart(firstMentionChart);
        sortChart(recommendChart);
    }

    private static double weightedAvg(List<TrendAgg> list, Function<TrendAgg, Double> valueFn) {
        int sample = list.stream().mapToInt(r -> r.sampleCount).sum();
        if (sample <= 0) {
            return 0;
        }
        double sum = 0;
        for (TrendAgg row : list) {
            sum += valueFn.apply(row) * row.sampleCount;
        }
        return round(sum / sample);
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

    private static String weekKey(GeoMonitorDaily record) {
        return weekKey(record.getInspectDate());
    }

    private static String weekKey(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return year + "-W" + String.format("%02d", week);
    }

    private static String weekLabel(GeoMonitorDaily record) {
        return weekLabel(record.getInspectDate());
    }

    private static String weekLabel(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return year + "年第" + week + "周";
    }

    private static LocalDate[] weekBounds(GeoMonitorDaily record) {
        LocalDate start = record.getInspectDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LocalDate[]{start, start.plusDays(6)};
    }

    private static String monthKey(GeoMonitorDaily record) {
        LocalDate date = record.getInspectDate();
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    private static String monthLabel(GeoMonitorDaily record) {
        LocalDate date = record.getInspectDate();
        return date.getYear() + "年" + String.format("%02d", date.getMonthValue()) + "月";
    }

    private static LocalDate[] monthBounds(GeoMonitorDaily record) {
        LocalDate date = record.getInspectDate();
        return new LocalDate[]{date.with(TemporalAdjusters.firstDayOfMonth()), date.with(TemporalAdjusters.lastDayOfMonth())};
    }

    private static String yearPeriodKey(String label, LocalDate start, LocalDate end) {
        return label + "@" + start + "~" + end;
    }

    private static String rowKey(String periodKey, Long topicId, String platform) {
        return periodKey + "\0" + topicId + "\0" + platform;
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

    private record TrendAgg(String periodKey, String axis, Long topicId, String topicName, String platform,
                            LocalDate periodStart, LocalDate periodEnd, int sampleCount, double mentionRate,
                            double firstMentionRate, int recommendCount, String competitorTop, String citePlatformTop,
                            boolean fromSnapshot) {
        static TrendAgg fromSnapshot(GeoBoardPeriodStat snap) {
            return new TrendAgg(
                    snap.getPeriodKey(),
                    snap.getPeriodLabel(),
                    snap.getTopicId(),
                    snap.getTopicName(),
                    snap.getPlatform(),
                    snap.getPeriodStart(),
                    snap.getPeriodEnd(),
                    snap.getSampleCount() == null ? 0 : snap.getSampleCount(),
                    snap.getMentionRate() == null ? 0 : snap.getMentionRate().doubleValue(),
                    snap.getFirstMentionRate() == null ? 0 : snap.getFirstMentionRate().doubleValue(),
                    snap.getRecommendCount() == null ? 0 : snap.getRecommendCount(),
                    snap.getCompetitorTop(),
                    snap.getCitePlatformTop(),
                    true);
        }
    }

    private record YearAgg(String periodKey, String periodLabel, Long topicId, String topicName, String platform,
                           LocalDate periodStart, LocalDate periodEnd, BigDecimal targetRate, double actualRate,
                           double achieveRate, int sampleCount, boolean fromSnapshot) {
        static YearAgg live(String periodKey, String periodLabel, Long topicId, String topicName, String platform,
                            LocalDate periodStart, LocalDate periodEnd, BigDecimal targetRate,
                            List<GeoMonitorDaily> list) {
            int total = list.size();
            int mentioned = (int) list.stream().filter(x -> Objects.equals(x.getMentioned(), 1)).count();
            double actual = pct(mentioned, total);
            double achieve = targetRate == null || targetRate.doubleValue() == 0 ? 0 : actual / targetRate.doubleValue() * 100;
            return new YearAgg(periodKey, periodLabel, topicId, topicName, platform, periodStart, periodEnd,
                    targetRate, round(actual), round(achieve), total, false);
        }

        static YearAgg fromSnapshot(GeoBoardPeriodStat snap) {
            return new YearAgg(
                    snap.getPeriodKey(),
                    snap.getPeriodLabel(),
                    snap.getTopicId(),
                    snap.getTopicName(),
                    snap.getPlatform(),
                    snap.getPeriodStart(),
                    snap.getPeriodEnd(),
                    snap.getTargetRate(),
                    snap.getActualRate() == null ? 0 : snap.getActualRate().doubleValue(),
                    snap.getAchieveRate() == null ? 0 : snap.getAchieveRate().doubleValue(),
                    snap.getSampleCount() == null ? 0 : snap.getSampleCount(),
                    true);
        }
    }
}
