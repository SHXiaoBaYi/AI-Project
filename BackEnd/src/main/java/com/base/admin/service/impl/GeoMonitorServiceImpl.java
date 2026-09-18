package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.Constants;
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
import com.base.admin.domain.entity.GeoPlatform;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.GeoYearTarget;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.enums.GeoPeriodType;
import com.base.admin.domain.vo.GeoChartPointVO;
import com.base.admin.domain.vo.GeoDailyBoardVO;
import com.base.admin.domain.vo.GeoDailyBulkConflictVO;
import com.base.admin.domain.vo.GeoDailyBulkSaveResultVO;
import com.base.admin.domain.vo.GeoDailyGroupVO;
import com.base.admin.domain.vo.GeoDailyVO;
import com.base.admin.domain.vo.GeoBoardCompareSummaryVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoLatestDateVO;
import com.base.admin.domain.vo.GeoMonthlyBoardVO;
import com.base.admin.domain.vo.GeoNegativeSummaryRowVO;
import com.base.admin.domain.vo.GeoOwnerOptionVO;
import com.base.admin.domain.vo.GeoPersistResultVO;
import com.base.admin.domain.vo.GeoTopicPlatformChartsVO;
import com.base.admin.domain.vo.GeoWeeklyBoardVO;
import com.base.admin.domain.vo.GeoYearlyBoardVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoBoardPeriodStatMapper;
import com.base.admin.mapper.GeoMonitorDailyMapper;
import com.base.admin.mapper.GeoTopicMapper;
import com.base.admin.mapper.GeoYearTargetMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.GeoMonitorService;
import com.base.admin.service.GeoPlatformService;
import com.base.admin.service.GeoTopicService;
import com.base.admin.util.ExcelCellUtils;
import com.base.admin.util.GeoExcelTemplateWriter;
import com.base.admin.util.GeoImportProgress;
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
import java.util.Collection;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeoMonitorServiceImpl implements GeoMonitorService {

    private static final int DATE_START_COL = 4;
    /** 每个日期下的指标块数：提及、排名、推荐、截图、负面、竞品 */
    private static final int METRIC_COUNT = 6;

    private final GeoMonitorDailyMapper dailyMapper;
    private final GeoTopicMapper topicMapper;
    private final GeoYearTargetMapper targetMapper;
    private final GeoBoardPeriodStatMapper periodStatMapper;
    private final GeoTopicService topicService;
    private final GeoPlatformService platformService;
    private final SysUserMapper userMapper;

    @Override
    public PageResult<GeoDailyVO> listDaily(GeoDailyQueryDTO query) {
        LambdaQueryWrapper<GeoMonitorDaily> wrapper = buildListDailyWrapper(query);
        wrapper.orderByDesc(GeoMonitorDaily::getInspectDate)
                .orderByDesc(GeoMonitorDaily::getUpdateTime)
                .orderByAsc(GeoMonitorDaily::getId);
        Page<GeoMonitorDaily> page = dailyMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, String> topicNames = topicNameMap();
        Map<Long, String> ownerNames = ownerDisplayMap(page.getRecords());
        List<GeoDailyVO> rows = page.getRecords().stream().map(e -> toVo(e, topicNames, ownerNames)).toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public GeoDailyVO getDaily(Long id) {
        GeoMonitorDaily entity = requireDaily(id);
        return toVo(entity, topicNameMap(), ownerDisplayMap(List.of(entity)));
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
            one.setTermType(dto.getTermType());
            one.setTopicId(dto.getTopicId());
            one.setKeyword(dto.getKeyword());
            one.setOwnerUserId(dto.getOwnerUserId());
            one.setOwnerName(dto.getOwnerName());
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
            String termType = normalizeTermType(group.getTermType());
            String ownerName = StringUtils.hasText(group.getOwnerName()) ? group.getOwnerName().trim() : null;
            String topicName = topicNames.getOrDefault(group.getTopicId(), "");
            boolean datePeriodLocked = isInspectDatePeriodLocked(group.getInspectDate());
            if (group.getItems() == null || group.getItems().isEmpty()) {
                throw new BusinessException("话题「" + topicName + "」请至少填写一个平台");
            }
            int platformCount = 0;

            for (GeoDailyPlatformItemDTO item : group.getItems()) {
                if (item == null || !StringUtils.hasText(item.getPlatform())) {
                    continue;
                }
                platformCount++;
                String platform = item.getPlatform().trim();
                String uk = group.getInspectDate() + "|" + platform + "|" + keyword + "|" + termType;
                if (!seenKeys.add(uk)) {
                    throw new BusinessException("提交数据存在重复：" + group.getInspectDate()
                            + " / " + platform + " / 「" + keyword + "」");
                }

                GeoDailyDTO one = new GeoDailyDTO();
                one.setId(item.getId());
                one.setInspectDate(group.getInspectDate());
                one.setTermType(termType);
                one.setTopicId(group.getTopicId());
                one.setKeyword(keyword);
                one.setOwnerUserId(group.getOwnerUserId());
                one.setOwnerName(ownerName);
                one.setPlatform(platform);
                one.setMentioned(item.getMentioned());
                one.setRankNo(item.getRankNo());
                one.setRecommendStatus(item.getRecommendStatus());
                one.setScreenshotUrl(item.getScreenshotUrl());
                one.setThirdPartyUrl(item.getThirdPartyUrl());
                one.setNegativeContent(item.getNegativeContent());
                one.setCompetitors(item.getCompetitors());

                GeoMonitorDaily existing = dailyMapper.selectUkIncludeDeleted(
                        group.getInspectDate(), platform, keyword, termType);
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
        String termType = normalizeTermType(dto.getTermType());
        dto.setTermType(termType);
        GeoMonitorDaily existing = dailyMapper.selectUkIncludeDeleted(dto.getInspectDate(), platform, keyword, termType);
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
        Map<Long, String> ownerNames = ownerDisplayMap(records);
        GeoDailyGroupVO group = new GeoDailyGroupVO();
        group.setInspectDate(inspectDate);
        group.setKeyword(keyword.trim());
        if (!records.isEmpty()) {
            GeoMonitorDaily first = records.getFirst();
            group.setTopicId(first.getTopicId());
            group.setTopicName(topicNames.getOrDefault(first.getTopicId(), ""));
            group.setTermType(normalizeTermType(first.getTermType()));
            group.setOwnerUserId(first.getOwnerUserId());
            group.setOwnerName(resolveOwnerName(first, ownerNames));
        }
        group.setItems(records.stream().map(e -> toVo(e, topicNames, ownerNames)).toList());
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
            if (sheet.getRow(0) != null) {
                lastCol = Math.max(lastCol, sheet.getRow(0).getLastCellNum());
            }
            if (sheet.getRow(2) != null) {
                lastCol = Math.max(lastCol, sheet.getRow(2).getLastCellNum());
            }
            List<DateGroup> groups = parseDateGroups(sheet, lastCol);
            int slots = 0;
            for (DateGroup group : groups) {
                slots += group.platforms.size();
            }
            int rowUnits = Math.max(slots, 1);
            int totalUnits = Math.max(Math.max(lastRow - 2, 0) * rowUnits, 1);
            int doneUnits = 0;
            String lastTopic = "";
            String lastWeek = "";
            for (int r = 3; r <= lastRow; r++) {
                String keyword = ExcelCellUtils.str(sheet, r, 3);
                if (!StringUtils.hasText(keyword) || keyword.contains(GeoExcelTemplateWriter.SAMPLE_MARK)) {
                    doneUnits += rowUnits;
                    GeoImportProgress.report(doneUnits, totalUnits);
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
                if (!StringUtils.hasText(lastTopic)) {
                    result.setTotalCount(result.getTotalCount() + 1);
                    result.setFailureCount(result.getFailureCount() + 1);
                    result.getErrors().add(new GeoImportResultVO.GeoImportErrorVO(r + 1, "话题", "话题不能为空"));
                    doneUnits += rowUnits;
                    GeoImportProgress.report(doneUnits, totalUnits);
                    continue;
                }
                // 名称已存在则只关联；不存在则建档后再关联
                GeoTopic topic = topicService.getOrCreate(lastTopic, lastWeek);
                for (DateGroup group : groups) {
                    int n = group.platformCount;
                    for (int i = 0; i < group.platforms.size(); i++) {
                        int p = group.platformIndex.get(i);
                        result.setTotalCount(result.getTotalCount() + 1);
                        try {
                            GeoDailyDTO dto = new GeoDailyDTO();
                            dto.setInspectDate(group.date);
                            dto.setPlatform(group.platforms.get(i));
                            platformService.getOrCreate(dto.getPlatform());
                            dto.setKeyword(keyword.trim());
                            dto.setTermType(Constants.TERM_TYPE_DAILY);
                            dto.setTopicId(topic.getId());
                            dto.setMentioned(parseMentioned(ExcelCellUtils.str(sheet, r, group.startCol + p)));
                            dto.setRankNo(parseRank(ExcelCellUtils.str(sheet, r, group.startCol + n + p)));
                            dto.setRecommendStatus(ExcelCellUtils.str(sheet, r, group.startCol + 2 * n + p));
                            String shot = ExcelCellUtils.str(sheet, r, group.startCol + 3 * n + p);
                            if (isHttp(shot)) {
                                dto.setThirdPartyUrl(shot);
                            }
                            dto.setNegativeContent(ExcelCellUtils.str(sheet, r, group.startCol + 4 * n + p));
                            dto.setCompetitors(ExcelCellUtils.str(sheet, r, group.startCol + 5 * n + p));
                            boolean inserted = upsertDaily(dto, true);
                            if (inserted) {
                                result.setInsertCount(result.getInsertCount() + 1);
                            } else {
                                result.setUpdateCount(result.getUpdateCount() + 1);
                            }
                        } catch (Exception e) {
                            result.setFailureCount(result.getFailureCount() + 1);
                            result.getErrors().add(new GeoImportResultVO.GeoImportErrorVO(
                                    r + 1, group.platforms.get(i), e.getMessage()));
                        }
                        doneUnits++;
                        GeoImportProgress.report(doneUnits, totalUnits);
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
        return platformService.listByType(Constants.PLATFORM_TYPE_AI).stream()
                .map(GeoPlatform::getPlatformName)
                .toList();
    }

    @Override
    public List<GeoOwnerOptionVO> listOwnerOptions() {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, Constants.STATUS_ACTIVE)
                .orderByAsc(SysUser::getUserId));
        List<GeoOwnerOptionVO> options = new ArrayList<>();
        for (SysUser user : users) {
            GeoOwnerOptionVO option = new GeoOwnerOptionVO();
            option.setUserId(user.getUserId());
            option.setUsername(user.getUsername());
            option.setNickname(user.getNickname());
            option.setDisplayName(userDisplayName(user));
            options.add(option);
        }
        return options;
    }

    @Override
    public GeoWeeklyBoardVO weeklyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = weekRange(query);
        LocalDate compareStart = range[0].minusYears(1).minusWeeks(1);
        List<TrendAgg> liveTopic = liveTrend(compareStart, range[1], query, BoardDim.TOPIC,
                GeoMonitorServiceImpl::weekKey, GeoMonitorServiceImpl::weekLabel, GeoMonitorServiceImpl::weekBounds);
        List<TrendAgg> liveOwner = liveTrend(compareStart, range[1], query, BoardDim.OWNER,
                GeoMonitorServiceImpl::weekKey, GeoMonitorServiceImpl::weekLabel, GeoMonitorServiceImpl::weekBounds);
        GeoWeeklyBoardVO board = buildWeeklyBoard(range, new LocalDate[]{compareStart, range[1]}, query, liveTopic);
        fillOwnerWeeklySlice(board, range, liveOwner);
        return board;
    }

    @Override
    public GeoMonthlyBoardVO monthlyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = monthRange(query);
        LocalDate compareStart = range[0].minusYears(1).minusMonths(1);
        List<TrendAgg> liveTopic = liveTrend(compareStart, range[1], query, BoardDim.TOPIC,
                GeoMonitorServiceImpl::monthKey, GeoMonitorServiceImpl::monthLabel, GeoMonitorServiceImpl::monthBounds);
        List<TrendAgg> liveOwner = liveTrend(compareStart, range[1], query, BoardDim.OWNER,
                GeoMonitorServiceImpl::monthKey, GeoMonitorServiceImpl::monthLabel, GeoMonitorServiceImpl::monthBounds);
        GeoMonthlyBoardVO board = buildMonthlyBoard(range, new LocalDate[]{compareStart, range[1]}, query, liveTopic);
        fillOwnerMonthlySlice(board, range, liveOwner);
        return board;
    }

    @Override
    public GeoDailyBoardVO dailyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = dayRange(query);
        List<GeoMonitorDaily> records = loadActiveDaily(
                range[0], range[1], query.getTopicId(), query.getKeyword(), query.getPlatforms(), query.getTermType());
        Map<Long, String> topicNames = topicNameMap();
        GeoDailyBoardVO board = new GeoDailyBoardVO();
        List<TrendAgg> topicRows = aggregateTrend(records, r -> r.getInspectDate().toString(),
                r -> r.getInspectDate().toString(), r -> new LocalDate[]{r.getInspectDate(), r.getInspectDate()},
                BoardDim.TOPIC, topicNames);
        for (TrendAgg row : topicRows) {
            GeoDailyBoardVO.GeoDailyRowVO vo = new GeoDailyBoardVO.GeoDailyRowVO();
            vo.setDateLabel(row.axis());
            vo.setTopicName(row.groupName());
            vo.setPlatform(row.platform());
            vo.setSampleCount(row.sampleCount());
            vo.setMentionRate(row.mentionRate());
            vo.setFirstMentionRate(row.firstMentionRate());
            vo.setRecommendCount(row.recommendCount());
            vo.setCompetitorTop(row.competitorTop());
            vo.setCitePlatformTop(row.citePlatformTop());
            board.getRows().add(vo);
        }
        fillChartsFromTrend(topicRows, board.getMentionChart(), board.getFirstMentionChart(), board.getRecommendChart());
        GeoTopicPlatformChartsVO tofu = buildTopicPlatformCharts(records, topicNames, null, null, "day");
        board.setRankChart(tofu.getRankChart());
        board.setSampleChart(tofu.getSampleChart());
        board.setNegativeChart(tofu.getNegativeChart());
        board.setSummaryGroups(buildDailySummaryGroups(records, topicNames));
        board.setCompareSummary(buildDailyPeriodCompare(range, query));
        fillNegativeSummary(records, topicNames, board);

        List<TrendAgg> ownerTrendRows = aggregateTrend(records, r -> r.getInspectDate().toString(),
                r -> r.getInspectDate().toString(), r -> new LocalDate[]{r.getInspectDate(), r.getInspectDate()},
                BoardDim.OWNER, topicNames);
        for (TrendAgg row : ownerTrendRows) {
            GeoDailyBoardVO.GeoDailyRowVO vo = new GeoDailyBoardVO.GeoDailyRowVO();
            vo.setDateLabel(row.axis());
            vo.setOwnerName(row.groupName());
            vo.setTopicName(row.groupName());
            vo.setPlatform(row.platform());
            vo.setSampleCount(row.sampleCount());
            vo.setMentionRate(row.mentionRate());
            vo.setFirstMentionRate(row.firstMentionRate());
            vo.setRecommendCount(row.recommendCount());
            vo.setCompetitorTop(row.competitorTop());
            vo.setCitePlatformTop(row.citePlatformTop());
            board.getOwnerRows().add(vo);
        }
        fillChartsFromTrend(ownerTrendRows, board.getOwnerMentionChart(), board.getOwnerFirstMentionChart(),
                board.getOwnerRecommendChart());
        board.setOwnerSummaryGroups(buildDailyOwnerSummaryGroups(records, topicNames));
        return board;
    }

    @Override
    public GeoTopicPlatformChartsVO topicPlatformCharts(GeoBoardQueryDTO query) {
        GeoBoardQueryDTO q = query == null ? new GeoBoardQueryDTO() : query;
        LocalDate[] range = dayRange(q);
        Long drillTopicId = q.getTopicId();
        String keyword = StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim() : null;
        String grain = StringUtils.hasText(q.getGrain()) ? q.getGrain().trim().toLowerCase() : "day";
        if (!List.of("day", "week", "month", "year").contains(grain)) {
            grain = "day";
        }
        // 下钻时按话题过滤；关键字在聚合内再筛，便于同请求返回问题列表
        List<GeoMonitorDaily> records = loadActiveDaily(
                range[0], range[1], drillTopicId, null, q.getPlatforms(), q.getTermType());
        Map<Long, String> topicNames = topicNameMap();
        return buildTopicPlatformCharts(records, topicNames, drillTopicId, keyword, grain);
    }

    @Override
    public List<GeoDailyVO> listNegativeDaily(GeoBoardQueryDTO query) {
        LocalDate[] range = dayRange(query == null ? new GeoBoardQueryDTO() : query);
        GeoBoardQueryDTO q = query == null ? new GeoBoardQueryDTO() : query;
        List<GeoMonitorDaily> records = loadActiveDaily(
                range[0], range[1], q.getTopicId(), q.getKeyword(), q.getPlatforms(), q.getTermType());
        Map<Long, String> topicNames = topicNameMap();
        Map<Long, String> ownerNames = ownerDisplayMap(records);
        return records.stream()
                .filter(r -> StringUtils.hasText(r.getNegativeContent()))
                .sorted(Comparator.comparing(GeoMonitorDaily::getInspectDate).reversed()
                        .thenComparing(GeoMonitorDaily::getPlatform))
                .map(r -> toVo(r, topicNames, ownerNames))
                .toList();
    }

    @Override
    public GeoYearlyBoardVO yearlyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = yearRange(query);
        LocalDate compareStart = LocalDate.of(range[0].getYear() - 2, 1, 1);
        List<YearAgg> liveTopic = liveYearly(compareStart, range[1], query, BoardDim.TOPIC);
        List<YearAgg> liveOwner = liveYearly(compareStart, range[1], query, BoardDim.OWNER);
        GeoYearlyBoardVO board = buildYearlyBoard(range, new LocalDate[]{compareStart, range[1]}, query, liveTopic);
        fillOwnerYearlySlice(board, range, liveOwner);
        return board;
    }

    @Override
    @Transactional
    public GeoPersistResultVO persistWeeklyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = weekRange(query);
        List<TrendAgg> live = liveTrend(range[0], range[1], query, BoardDim.TOPIC, GeoMonitorServiceImpl::weekKey,
                GeoMonitorServiceImpl::weekLabel, GeoMonitorServiceImpl::weekBounds);
        return persistTrend(GeoPeriodType.WEEK, live, true);
    }

    @Override
    @Transactional
    public GeoPersistResultVO persistMonthlyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = monthRange(query);
        List<TrendAgg> live = liveTrend(range[0], range[1], query, BoardDim.TOPIC, GeoMonitorServiceImpl::monthKey,
                GeoMonitorServiceImpl::monthLabel, GeoMonitorServiceImpl::monthBounds);
        return persistTrend(GeoPeriodType.MONTH, live, true);
    }

    @Override
    @Transactional
    public GeoPersistResultVO persistYearlyBoard(GeoBoardQueryDTO query) {
        LocalDate[] range = yearRange(query);
        List<YearAgg> live = liveYearly(range[0], range[1], query, BoardDim.TOPIC);
        return persistYearly(live, true);
    }

    @Override
    @Transactional
    public GeoPersistResultVO autoPersistCompletedWeekly(int lookbackWeeks) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        int weeks = Math.max(lookbackWeeks, 1);
        LocalDate start = end.minusWeeks(weeks - 1L).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<TrendAgg> live = liveTrend(start, end, new GeoBoardQueryDTO(), BoardDim.TOPIC, GeoMonitorServiceImpl::weekKey,
                GeoMonitorServiceImpl::weekLabel, GeoMonitorServiceImpl::weekBounds).stream()
                .filter(r -> r.periodEnd().isBefore(today))
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
        List<TrendAgg> live = liveTrend(start, end, new GeoBoardQueryDTO(), BoardDim.TOPIC, GeoMonitorServiceImpl::monthKey,
                GeoMonitorServiceImpl::monthLabel, GeoMonitorServiceImpl::monthBounds).stream()
                .filter(r -> r.periodEnd().isBefore(today))
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
        List<YearAgg> live = liveYearly(start, end, new GeoBoardQueryDTO(), BoardDim.TOPIC).stream()
                .filter(r -> r.periodEnd().isBefore(today))
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

    private List<TrendAgg> liveTrend(LocalDate start, LocalDate end, GeoBoardQueryDTO query, BoardDim dim,
                                     Function<GeoMonitorDaily, String> keyFn,
                                     Function<GeoMonitorDaily, String> labelFn,
                                     Function<GeoMonitorDaily, LocalDate[]> boundsFn) {
        List<GeoMonitorDaily> records = loadActiveDaily(
                start, end, query.getTopicId(), query.getKeyword(), query.getPlatforms(), query.getTermType());
        return aggregateTrend(records, keyFn, labelFn, boundsFn, dim, topicNameMap());
    }

    private GeoWeeklyBoardVO buildWeeklyBoard(LocalDate[] displayRange, LocalDate[] compareRange,
                                              GeoBoardQueryDTO query, List<TrendAgg> live) {
        Map<String, TrendAgg> merged = mergeTrendWithSnapshots(GeoPeriodType.WEEK, compareRange, query, live);
        GeoWeeklyBoardVO board = new GeoWeeklyBoardVO();
        Set<String> persistedKeys = new HashSet<>();
        List<TrendAgg> displayRows = new ArrayList<>();
        for (TrendAgg row : merged.values()) {
            if (row.periodEnd().isBefore(displayRange[0]) || row.periodStart().isAfter(displayRange[1])) {
                continue;
            }
            displayRows.add(row);
            GeoWeeklyBoardVO.GeoWeeklyRowVO vo = new GeoWeeklyBoardVO.GeoWeeklyRowVO();
            vo.setWeekLabel(row.axis());
            vo.setTopicName(row.groupName());
            vo.setPlatform(row.platform());
            vo.setSampleCount(row.sampleCount());
            vo.setMentionRate(row.mentionRate());
            vo.setFirstMentionRate(row.firstMentionRate());
            vo.setRecommendCount(row.recommendCount());
            vo.setCompetitorTop(row.competitorTop());
            vo.setCitePlatformTop(row.citePlatformTop());
            vo.setFromSnapshot(row.fromSnapshot());
            fillTrendCompare(vo, row, merged, true);
            board.getRows().add(vo);
            if (row.fromSnapshot()) {
                persistedKeys.add(row.periodKey());
            }
        }
        board.getRows().sort(Comparator.comparing(GeoWeeklyBoardVO.GeoWeeklyRowVO::getWeekLabel)
                .thenComparing(GeoWeeklyBoardVO.GeoWeeklyRowVO::getTopicName)
                .thenComparing(GeoWeeklyBoardVO.GeoWeeklyRowVO::getPlatform));
        Map<String, TrendAgg> displayMap = new LinkedHashMap<>();
        for (TrendAgg row : displayRows) {
            displayMap.put(rowKey(row), row);
        }
        fillChartsFromTrend(displayMap.values(), board.getMentionChart(), board.getFirstMentionChart(), board.getRecommendChart());
        board.setPersistedPeriodCount(persistedKeys.size());
        board.setCompareSummary(buildTrendCompareSummary(displayRows, merged, true, "环比=上一周；同比=去年同周"));
        return board;
    }

    private void fillOwnerWeeklySlice(GeoWeeklyBoardVO board, LocalDate[] displayRange, List<TrendAgg> liveOwner) {
        Map<String, TrendAgg> merged = toTrendMap(liveOwner);
        List<TrendAgg> displayRows = filterTrendDisplay(merged.values(), displayRange);
        for (TrendAgg row : displayRows) {
            GeoWeeklyBoardVO.GeoWeeklyRowVO vo = new GeoWeeklyBoardVO.GeoWeeklyRowVO();
            vo.setWeekLabel(row.axis());
            vo.setOwnerName(row.groupName());
            vo.setTopicName(row.groupName());
            vo.setPlatform(row.platform());
            vo.setSampleCount(row.sampleCount());
            vo.setMentionRate(row.mentionRate());
            vo.setFirstMentionRate(row.firstMentionRate());
            vo.setRecommendCount(row.recommendCount());
            vo.setCompetitorTop(row.competitorTop());
            vo.setCitePlatformTop(row.citePlatformTop());
            vo.setFromSnapshot(false);
            fillTrendCompare(vo, row, merged, true);
            board.getOwnerRows().add(vo);
        }
        board.getOwnerRows().sort(Comparator.comparing(GeoWeeklyBoardVO.GeoWeeklyRowVO::getWeekLabel)
                .thenComparing(r -> r.getOwnerName() == null ? "" : r.getOwnerName())
                .thenComparing(GeoWeeklyBoardVO.GeoWeeklyRowVO::getPlatform));
        fillChartsFromTrend(displayRows, board.getOwnerMentionChart(), board.getOwnerFirstMentionChart(),
                board.getOwnerRecommendChart());
        board.setOwnerCompareSummary(buildTrendCompareSummary(displayRows, merged, true, "环比=上一周；同比=去年同周"));
    }

    private GeoMonthlyBoardVO buildMonthlyBoard(LocalDate[] displayRange, LocalDate[] compareRange,
                                                GeoBoardQueryDTO query, List<TrendAgg> live) {
        Map<String, TrendAgg> merged = mergeTrendWithSnapshots(GeoPeriodType.MONTH, compareRange, query, live);
        GeoMonthlyBoardVO board = new GeoMonthlyBoardVO();
        Set<String> persistedKeys = new HashSet<>();
        List<TrendAgg> displayRows = new ArrayList<>();
        for (TrendAgg row : merged.values()) {
            if (row.periodEnd().isBefore(displayRange[0]) || row.periodStart().isAfter(displayRange[1])) {
                continue;
            }
            displayRows.add(row);
            GeoMonthlyBoardVO.GeoMonthlyRowVO vo = new GeoMonthlyBoardVO.GeoMonthlyRowVO();
            vo.setMonthLabel(row.axis());
            vo.setTopicName(row.groupName());
            vo.setPlatform(row.platform());
            vo.setSampleCount(row.sampleCount());
            vo.setMentionRate(row.mentionRate());
            vo.setFirstMentionRate(row.firstMentionRate());
            vo.setRecommendCount(row.recommendCount());
            vo.setCompetitorTop(row.competitorTop());
            vo.setCitePlatformTop(row.citePlatformTop());
            vo.setFromSnapshot(row.fromSnapshot());
            fillTrendCompare(vo, row, merged, false);
            board.getRows().add(vo);
            if (row.fromSnapshot()) {
                persistedKeys.add(row.periodKey());
            }
        }
        board.getRows().sort(Comparator.comparing(GeoMonthlyBoardVO.GeoMonthlyRowVO::getMonthLabel)
                .thenComparing(GeoMonthlyBoardVO.GeoMonthlyRowVO::getTopicName)
                .thenComparing(GeoMonthlyBoardVO.GeoMonthlyRowVO::getPlatform));
        Map<String, TrendAgg> displayMap = new LinkedHashMap<>();
        for (TrendAgg row : displayRows) {
            displayMap.put(rowKey(row), row);
        }
        fillChartsFromTrend(displayMap.values(), board.getMentionChart(), board.getFirstMentionChart(), board.getRecommendChart());
        board.setPersistedPeriodCount(persistedKeys.size());
        board.setCompareSummary(buildTrendCompareSummary(displayRows, merged, false, "环比=上一月；同比=去年同月"));
        return board;
    }

    private void fillOwnerMonthlySlice(GeoMonthlyBoardVO board, LocalDate[] displayRange, List<TrendAgg> liveOwner) {
        Map<String, TrendAgg> merged = toTrendMap(liveOwner);
        List<TrendAgg> displayRows = filterTrendDisplay(merged.values(), displayRange);
        for (TrendAgg row : displayRows) {
            GeoMonthlyBoardVO.GeoMonthlyRowVO vo = new GeoMonthlyBoardVO.GeoMonthlyRowVO();
            vo.setMonthLabel(row.axis());
            vo.setOwnerName(row.groupName());
            vo.setTopicName(row.groupName());
            vo.setPlatform(row.platform());
            vo.setSampleCount(row.sampleCount());
            vo.setMentionRate(row.mentionRate());
            vo.setFirstMentionRate(row.firstMentionRate());
            vo.setRecommendCount(row.recommendCount());
            vo.setCompetitorTop(row.competitorTop());
            vo.setCitePlatformTop(row.citePlatformTop());
            vo.setFromSnapshot(false);
            fillTrendCompare(vo, row, merged, false);
            board.getOwnerRows().add(vo);
        }
        board.getOwnerRows().sort(Comparator.comparing(GeoMonthlyBoardVO.GeoMonthlyRowVO::getMonthLabel)
                .thenComparing(r -> r.getOwnerName() == null ? "" : r.getOwnerName())
                .thenComparing(GeoMonthlyBoardVO.GeoMonthlyRowVO::getPlatform));
        fillChartsFromTrend(displayRows, board.getOwnerMentionChart(), board.getOwnerFirstMentionChart(),
                board.getOwnerRecommendChart());
        board.setOwnerCompareSummary(buildTrendCompareSummary(displayRows, merged, false, "环比=上一月；同比=去年同月"));
    }

    private GeoYearlyBoardVO buildYearlyBoard(LocalDate[] displayRange, LocalDate[] compareRange,
                                             GeoBoardQueryDTO query, List<YearAgg> live) {
        Map<String, YearAgg> merged = mergeYearlyWithSnapshots(compareRange, query, live);
        GeoYearlyBoardVO board = new GeoYearlyBoardVO();
        List<String> platforms = resolveYearlyPlatforms(query, merged.values());
        board.setPlatforms(platforms);
        Set<String> persistedKeys = new HashSet<>();
        List<YearAgg> displayRows = new ArrayList<>();
        for (YearAgg row : merged.values()) {
            if (row.periodEnd().isBefore(displayRange[0]) || row.periodStart().isAfter(displayRange[1])) {
                continue;
            }
            if (!StringUtils.hasText(row.platform()) || "-".equals(row.platform())) {
                continue;
            }
            displayRows.add(row);
            GeoYearlyBoardVO.GeoYearlyRowVO vo = new GeoYearlyBoardVO.GeoYearlyRowVO();
            vo.setPeriodLabel(row.periodLabel());
            vo.setTopicName(row.groupName());
            vo.setPlatform(row.platform());
            vo.setTargetRate(row.targetRate());
            vo.setActualRate(row.actualRate());
            vo.setAchieveRate(row.achieveRate());
            vo.setSampleCount(row.sampleCount());
            vo.setFromSnapshot(row.fromSnapshot());
            fillYearCompare(vo, row, merged);
            board.getRows().add(vo);
            addChart(board.getActualChart(), row.periodLabel() + " / " + row.groupName(), row.platform(), row.actualRate());
            addChart(board.getAchieveChart(), row.periodLabel() + " / " + row.groupName(), row.platform(), row.achieveRate());
            if (row.fromSnapshot()) {
                persistedKeys.add(row.periodKey());
            }
        }
        board.getRows().sort(Comparator.comparing(GeoYearlyBoardVO.GeoYearlyRowVO::getPeriodLabel)
                .thenComparing(GeoYearlyBoardVO.GeoYearlyRowVO::getTopicName)
                .thenComparing(GeoYearlyBoardVO.GeoYearlyRowVO::getPlatform));
        sortChart(board.getActualChart());
        sortChart(board.getAchieveChart());
        board.setPersistedPeriodCount(persistedKeys.size());
        board.setCompareSummary(buildYearCompareSummary(displayRows, merged));
        board.setOverallAchieveRates(buildOverallAchieveRates(displayRange, query, displayRows, platforms));
        return board;
    }

    private List<String> resolveYearlyPlatforms(GeoBoardQueryDTO query, Collection<YearAgg> rows) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (query.getPlatforms() != null) {
            query.getPlatforms().stream().filter(StringUtils::hasText).forEach(ordered::add);
        }
        for (String name : listPlatforms()) {
            ordered.add(name);
        }
        for (YearAgg row : rows) {
            if (StringUtils.hasText(row.platform()) && !"-".equals(row.platform())) {
                ordered.add(row.platform());
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 对齐样例表「全年目标达成率」：各平台 SUM(达成率) / 话题行总数（含未填行，未填视为 0）。
     */
    private List<GeoYearlyBoardVO.PlatformOverallVO> buildOverallAchieveRates(
            LocalDate[] displayRange, GeoBoardQueryDTO query, List<YearAgg> displayRows, List<String> platforms) {
        int totalSlots = countYearlyTopicSlots(displayRange, query);
        if (totalSlots <= 0) {
            Set<String> keys = new LinkedHashSet<>();
            for (YearAgg row : displayRows) {
                keys.add(row.periodLabel() + "\0" + row.groupName());
            }
            totalSlots = Math.max(keys.size(), 1);
        }
        Map<String, Double> sumAchieve = new LinkedHashMap<>();
        Map<String, Integer> filled = new LinkedHashMap<>();
        for (String platform : platforms) {
            sumAchieve.put(platform, 0d);
            filled.put(platform, 0);
        }
        for (YearAgg row : displayRows) {
            if (!sumAchieve.containsKey(row.platform())) {
                continue;
            }
            sumAchieve.merge(row.platform(), row.achieveRate(), Double::sum);
            filled.merge(row.platform(), 1, Integer::sum);
        }
        List<GeoYearlyBoardVO.PlatformOverallVO> list = new ArrayList<>();
        int denom = totalSlots;
        for (String platform : platforms) {
            int fill = filled.getOrDefault(platform, 0);
            if (fill <= 0) {
                continue;
            }
            GeoYearlyBoardVO.PlatformOverallVO vo = new GeoYearlyBoardVO.PlatformOverallVO();
            vo.setPlatform(platform);
            vo.setAchieveRate(round(sumAchieve.getOrDefault(platform, 0d) / denom));
            vo.setFilledCount(fill);
            vo.setTotalCount(denom);
            list.add(vo);
        }
        return list;
    }

    private int countYearlyTopicSlots(LocalDate[] displayRange, GeoBoardQueryDTO query) {
        List<GeoYearTarget> targets = targetMapper.selectList(new LambdaQueryWrapper<GeoYearTarget>()
                .eq(query.getTopicId() != null, GeoYearTarget::getTopicId, query.getTopicId())
                .orderByAsc(GeoYearTarget::getSortOrder));
        int count = 0;
        for (GeoYearTarget target : targets) {
            if (!overlaps(target, displayRange[0], displayRange[1])) {
                continue;
            }
            count++;
        }
        return count;
    }

    private void fillOwnerYearlySlice(GeoYearlyBoardVO board, LocalDate[] displayRange, List<YearAgg> liveOwner) {
        Map<String, YearAgg> merged = toYearMap(liveOwner);
        List<YearAgg> displayRows = new ArrayList<>();
        for (YearAgg row : merged.values()) {
            if (row.periodEnd().isBefore(displayRange[0]) || row.periodStart().isAfter(displayRange[1])) {
                continue;
            }
            displayRows.add(row);
            GeoYearlyBoardVO.GeoYearlyRowVO vo = new GeoYearlyBoardVO.GeoYearlyRowVO();
            vo.setPeriodLabel(row.periodLabel());
            vo.setOwnerName(row.groupName());
            vo.setTopicName(row.groupName());
            vo.setPlatform(row.platform());
            vo.setTargetRate(row.targetRate());
            vo.setActualRate(row.actualRate());
            vo.setAchieveRate(row.achieveRate());
            vo.setSampleCount(row.sampleCount());
            vo.setFromSnapshot(false);
            fillYearCompare(vo, row, merged);
            board.getOwnerRows().add(vo);
            addChart(board.getOwnerActualChart(), row.periodLabel() + " / " + row.groupName(), row.platform(), row.actualRate());
            addChart(board.getOwnerAchieveChart(), row.periodLabel() + " / " + row.groupName(), row.platform(), row.achieveRate());
        }
        board.getOwnerRows().sort(Comparator.comparing(GeoYearlyBoardVO.GeoYearlyRowVO::getPeriodLabel)
                .thenComparing(r -> r.getOwnerName() == null ? "" : r.getOwnerName())
                .thenComparing(GeoYearlyBoardVO.GeoYearlyRowVO::getPlatform));
        sortChart(board.getOwnerActualChart());
        sortChart(board.getOwnerAchieveChart());
        board.setOwnerCompareSummary(buildYearCompareSummary(displayRows, merged));
    }

    private static Map<String, TrendAgg> toTrendMap(List<TrendAgg> rows) {
        Map<String, TrendAgg> merged = new LinkedHashMap<>();
        for (TrendAgg row : rows) {
            merged.put(rowKey(row), row);
        }
        return merged;
    }

    private static Map<String, YearAgg> toYearMap(List<YearAgg> rows) {
        Map<String, YearAgg> merged = new LinkedHashMap<>();
        for (YearAgg row : rows) {
            merged.put(rowKey(row), row);
        }
        return merged;
    }

    private static List<TrendAgg> filterTrendDisplay(Collection<TrendAgg> rows, LocalDate[] displayRange) {
        List<TrendAgg> displayRows = new ArrayList<>();
        for (TrendAgg row : rows) {
            if (row.periodEnd().isBefore(displayRange[0]) || row.periodStart().isAfter(displayRange[1])) {
                continue;
            }
            displayRows.add(row);
        }
        return displayRows;
    }

    private List<GeoDailyBoardVO.GeoDailySummaryDateVO> buildDailySummaryGroups(
            List<GeoMonitorDaily> records, Map<Long, String> topicNames) {
        Map<Long, String> ownerNames = ownerDisplayMap(records);
        Map<String, Map<String, GeoDailyBoardVO.GeoDailySummaryTopicVO>> byDate = new LinkedHashMap<>();
        for (GeoMonitorDaily record : records) {
            if (!isActiveDaily(record) || record.getInspectDate() == null) {
                continue;
            }
            String date = record.getInspectDate().toString();
            String keyword = record.getKeyword() == null ? "" : record.getKeyword();
            String termType = normalizeTermType(record.getTermType());
            String topicKey = (record.getTopicId() == null ? "0" : record.getTopicId()) + "\0" + keyword + "\0" + termType;
            Map<String, GeoDailyBoardVO.GeoDailySummaryTopicVO> topics =
                    byDate.computeIfAbsent(date, k -> new LinkedHashMap<>());
            GeoDailyBoardVO.GeoDailySummaryTopicVO topic = topics.computeIfAbsent(topicKey, id -> {
                GeoDailyBoardVO.GeoDailySummaryTopicVO t = new GeoDailyBoardVO.GeoDailySummaryTopicVO();
                t.setTopicId(record.getTopicId());
                t.setTopicName(topicNames.getOrDefault(record.getTopicId(), ""));
                t.setKeyword(keyword);
                t.setTermType(termType);
                t.setOwnerUserId(record.getOwnerUserId());
                t.setOwnerName(resolveOwnerName(record, ownerNames));
                return t;
            });
            if (!StringUtils.hasText(topic.getOwnerName())) {
                String ownerName = resolveOwnerName(record, ownerNames);
                if (StringUtils.hasText(ownerName)) {
                    topic.setOwnerName(ownerName);
                    topic.setOwnerUserId(record.getOwnerUserId());
                }
            }
            GeoDailyBoardVO.GeoDailySummaryPlatformVO platform = new GeoDailyBoardVO.GeoDailySummaryPlatformVO();
            platform.setId(record.getId());
            platform.setPlatform(record.getPlatform());
            platform.setMentioned(record.getMentioned() == null ? 0 : record.getMentioned());
            platform.setRankNo(record.getRankNo());
            platform.setRecommendStatus(record.getRecommendStatus());
            platform.setThirdPartyUrl(record.getThirdPartyUrl());
            platform.setCompetitors(record.getCompetitors());
            platform.setNegativeContent(record.getNegativeContent());
            platform.setScreenshotUrl(record.getScreenshotUrl());
            topic.getPlatforms().add(platform);
        }
        List<GeoDailyBoardVO.GeoDailySummaryDateVO> groups = new ArrayList<>();
        for (Map.Entry<String, Map<String, GeoDailyBoardVO.GeoDailySummaryTopicVO>> e : byDate.entrySet()) {
            GeoDailyBoardVO.GeoDailySummaryDateVO dateVo = new GeoDailyBoardVO.GeoDailySummaryDateVO();
            dateVo.setInspectDate(e.getKey());
            dateVo.getTopics().addAll(e.getValue().values());
            groups.add(dateVo);
        }
        groups.sort(Comparator.comparing(GeoDailyBoardVO.GeoDailySummaryDateVO::getInspectDate).reversed());
        return groups;
    }

    private List<GeoDailyBoardVO.GeoDailySummaryDateVO> buildDailyOwnerSummaryGroups(
            List<GeoMonitorDaily> records, Map<Long, String> topicNames) {
        Map<String, Map<String, GeoDailyBoardVO.GeoDailySummaryTopicVO>> byDate = new LinkedHashMap<>();
        Map<Long, String> ownerNames = ownerDisplayMap(records);
        for (GeoMonitorDaily record : records) {
            if (!isActiveDaily(record) || record.getInspectDate() == null) {
                continue;
            }
            String date = record.getInspectDate().toString();
            String ownerKey = ownerDimKey(record);
            String ownerName = ownerDimName(resolveOwnerName(record, ownerNames));
            Map<String, GeoDailyBoardVO.GeoDailySummaryTopicVO> owners =
                    byDate.computeIfAbsent(date, k -> new LinkedHashMap<>());
            GeoDailyBoardVO.GeoDailySummaryTopicVO owner = owners.computeIfAbsent(ownerKey, id -> {
                GeoDailyBoardVO.GeoDailySummaryTopicVO t = new GeoDailyBoardVO.GeoDailySummaryTopicVO();
                t.setTopicId(0L);
                t.setTopicName(ownerName);
                t.setOwnerName(ownerName);
                t.setOwnerUserId(record.getOwnerUserId());
                t.setKeyword("");
                return t;
            });
            GeoDailyBoardVO.GeoDailySummaryPlatformVO platform = new GeoDailyBoardVO.GeoDailySummaryPlatformVO();
            platform.setId(record.getId());
            platform.setPlatform(record.getPlatform());
            platform.setTopicName(topicNames.getOrDefault(record.getTopicId(), ""));
            platform.setKeyword(record.getKeyword());
            platform.setMentioned(record.getMentioned() == null ? 0 : record.getMentioned());
            platform.setRankNo(record.getRankNo());
            platform.setRecommendStatus(record.getRecommendStatus());
            platform.setThirdPartyUrl(record.getThirdPartyUrl());
            platform.setCompetitors(record.getCompetitors());
            platform.setNegativeContent(record.getNegativeContent());
            platform.setScreenshotUrl(record.getScreenshotUrl());
            owner.getPlatforms().add(platform);
        }
        List<GeoDailyBoardVO.GeoDailySummaryDateVO> groups = new ArrayList<>();
        for (Map.Entry<String, Map<String, GeoDailyBoardVO.GeoDailySummaryTopicVO>> e : byDate.entrySet()) {
            GeoDailyBoardVO.GeoDailySummaryDateVO dateVo = new GeoDailyBoardVO.GeoDailySummaryDateVO();
            dateVo.setInspectDate(e.getKey());
            dateVo.getTopics().addAll(e.getValue().values());
            groups.add(dateVo);
        }
        groups.sort(Comparator.comparing(GeoDailyBoardVO.GeoDailySummaryDateVO::getInspectDate).reversed());
        return groups;
    }

    private void fillTrendCompare(Object vo, TrendAgg row, Map<String, TrendAgg> merged, boolean weekMode) {
        TrendAgg mom = merged.get(rowKey(shiftPeriodKey(row.periodKey, weekMode, -1), row.groupKey, row.platform));
        TrendAgg yoy = merged.get(rowKey(shiftPeriodKey(row.periodKey, weekMode, weekMode ? -52 : -12), row.groupKey, row.platform));
        if (yoy == null) {
            LocalDate yoyDate = weekMode ? row.periodStart.minusWeeks(52) : row.periodStart.minusYears(1);
            String yoyKey = weekMode ? weekKey(yoyDate) : monthKey(yoyDate);
            yoy = merged.get(rowKey(yoyKey, row.groupKey, row.platform));
        }
        if (vo instanceof GeoWeeklyBoardVO.GeoWeeklyRowVO w) {
            w.setMentionRateMom(deltaRate(row.mentionRate, mom == null ? null : mom.mentionRate));
            w.setMentionRateYoy(deltaRate(row.mentionRate, yoy == null ? null : yoy.mentionRate));
            w.setFirstMentionRateMom(deltaRate(row.firstMentionRate, mom == null ? null : mom.firstMentionRate));
            w.setFirstMentionRateYoy(deltaRate(row.firstMentionRate, yoy == null ? null : yoy.firstMentionRate));
            w.setRecommendCountMom(deltaCount(row.recommendCount, mom == null ? null : mom.recommendCount));
            w.setRecommendCountYoy(deltaCount(row.recommendCount, yoy == null ? null : yoy.recommendCount));
        } else if (vo instanceof GeoMonthlyBoardVO.GeoMonthlyRowVO m) {
            m.setMentionRateMom(deltaRate(row.mentionRate, mom == null ? null : mom.mentionRate));
            m.setMentionRateYoy(deltaRate(row.mentionRate, yoy == null ? null : yoy.mentionRate));
            m.setFirstMentionRateMom(deltaRate(row.firstMentionRate, mom == null ? null : mom.firstMentionRate));
            m.setFirstMentionRateYoy(deltaRate(row.firstMentionRate, yoy == null ? null : yoy.firstMentionRate));
            m.setRecommendCountMom(deltaCount(row.recommendCount, mom == null ? null : mom.recommendCount));
            m.setRecommendCountYoy(deltaCount(row.recommendCount, yoy == null ? null : yoy.recommendCount));
        }
    }

    private void fillYearCompare(GeoYearlyBoardVO.GeoYearlyRowVO vo, YearAgg row, Map<String, YearAgg> merged) {
        YearAgg mom = findYearNeighbor(merged, row, -1);
        YearAgg yoy = findYearNeighbor(merged, row, -1);
        vo.setActualRateMom(deltaRate(row.actualRate, mom == null ? null : mom.actualRate));
        vo.setActualRateYoy(deltaRate(row.actualRate, yoy == null ? null : yoy.actualRate));
        vo.setAchieveRateMom(deltaRate(row.achieveRate, mom == null ? null : mom.achieveRate));
        vo.setAchieveRateYoy(deltaRate(row.achieveRate, yoy == null ? null : yoy.achieveRate));
    }

    private YearAgg findYearNeighbor(Map<String, YearAgg> merged, YearAgg row, int yearOffset) {
        LocalDate target = row.periodStart.plusYears(yearOffset);
        for (YearAgg item : merged.values()) {
            if (!Objects.equals(item.groupKey, row.groupKey) || !Objects.equals(item.platform, row.platform)) {
                continue;
            }
            if (item.periodStart.getYear() == target.getYear()) {
                return item;
            }
        }
        return null;
    }

    private GeoBoardCompareSummaryVO buildTrendCompareSummary(List<TrendAgg> displayRows,
                                                             Map<String, TrendAgg> merged,
                                                             boolean weekMode,
                                                             String hint) {
        GeoBoardCompareSummaryVO summary = new GeoBoardCompareSummaryVO();
        summary.setCompareHint(hint);
        if (displayRows.isEmpty()) {
            return summary;
        }
        LocalDate latestStart = displayRows.stream().map(r -> r.periodStart).max(LocalDate::compareTo).orElse(null);
        List<TrendAgg> latest = displayRows.stream().filter(r -> Objects.equals(r.periodStart, latestStart)).toList();
        MetricBag current = aggregateTrendMetrics(latest);
        List<TrendAgg> momRows = new ArrayList<>();
        List<TrendAgg> yoyRows = new ArrayList<>();
        for (TrendAgg row : latest) {
            TrendAgg mom = merged.get(rowKey(shiftPeriodKey(row.periodKey, weekMode, -1), row.groupKey, row.platform));
            if (mom != null) {
                momRows.add(mom);
            }
            LocalDate yoyDate = weekMode ? row.periodStart.minusWeeks(52) : row.periodStart.minusYears(1);
            String yoyKey = weekMode ? weekKey(yoyDate) : monthKey(yoyDate);
            TrendAgg yoy = merged.get(rowKey(yoyKey, row.groupKey, row.platform));
            if (yoy != null) {
                yoyRows.add(yoy);
            }
        }
        MetricBag mom = momRows.isEmpty() ? null : aggregateTrendMetrics(momRows);
        MetricBag yoy = yoyRows.isEmpty() ? null : aggregateTrendMetrics(yoyRows);
        summary.setMentionRate(current.mentionRate);
        summary.setFirstMentionRate(current.firstMentionRate);
        summary.setRecommendCount(current.recommendCount);
        summary.setSampleCount(current.sampleCount);
        summary.setMentionRateMom(mom == null ? null : deltaRate(current.mentionRate, Double.valueOf(mom.mentionRate)));
        summary.setMentionRateYoy(yoy == null ? null : deltaRate(current.mentionRate, Double.valueOf(yoy.mentionRate)));
        summary.setFirstMentionRateMom(mom == null ? null : deltaRate(current.firstMentionRate, Double.valueOf(mom.firstMentionRate)));
        summary.setFirstMentionRateYoy(yoy == null ? null : deltaRate(current.firstMentionRate, Double.valueOf(yoy.firstMentionRate)));
        summary.setRecommendCountMom(mom == null ? null : deltaCount(current.recommendCount, Integer.valueOf(mom.recommendCount)));
        summary.setRecommendCountYoy(yoy == null ? null : deltaCount(current.recommendCount, Integer.valueOf(yoy.recommendCount)));
        return summary;
    }

    private GeoBoardCompareSummaryVO buildYearCompareSummary(List<YearAgg> displayRows, Map<String, YearAgg> merged) {
        GeoBoardCompareSummaryVO summary = new GeoBoardCompareSummaryVO();
        summary.setCompareHint("环比/同比=上一年度同期");
        if (displayRows.isEmpty()) {
            return summary;
        }
        int latestYear = displayRows.stream().mapToInt(r -> r.periodStart.getYear()).max().orElse(0);
        List<YearAgg> latest = displayRows.stream().filter(r -> r.periodStart.getYear() == latestYear).toList();
        double actual = weightedActual(latest);
        int recommendProxy = latest.stream().mapToInt(r -> r.sampleCount).sum();
        double firstProxy = actual;
        summary.setMentionRate(round(actual));
        summary.setFirstMentionRate(round(firstProxy));
        summary.setRecommendCount(recommendProxy);
        summary.setSampleCount(recommendProxy);
        List<YearAgg> prev = new ArrayList<>();
        for (YearAgg row : latest) {
            YearAgg n = findYearNeighbor(merged, row, -1);
            if (n != null) {
                prev.add(n);
            }
        }
        double prevActual = prev.isEmpty() ? Double.NaN : weightedActual(prev);
        summary.setMentionRateMom(prev.isEmpty() ? null : deltaRate(actual, prevActual));
        summary.setMentionRateYoy(prev.isEmpty() ? null : deltaRate(actual, prevActual));
        summary.setFirstMentionRateMom(prev.isEmpty() ? null : deltaRate(firstProxy, prevActual));
        summary.setFirstMentionRateYoy(prev.isEmpty() ? null : deltaRate(firstProxy, prevActual));
        Integer prevSample = prev.isEmpty() ? null : prev.stream().mapToInt(r -> r.sampleCount).sum();
        summary.setRecommendCountMom(deltaCount(recommendProxy, prevSample));
        summary.setRecommendCountYoy(summary.getRecommendCountMom());
        return summary;
    }

    private static double weightedActual(List<YearAgg> rows) {
        int total = rows.stream().mapToInt(r -> r.sampleCount).sum();
        if (total <= 0) {
            return rows.stream().mapToDouble(r -> r.actualRate).average().orElse(0);
        }
        double sum = 0;
        for (YearAgg row : rows) {
            sum += row.actualRate * row.sampleCount;
        }
        return sum / total;
    }

    private static MetricBag aggregateTrendMetrics(List<TrendAgg> rows) {
        MetricBag bag = new MetricBag();
        if (rows == null || rows.isEmpty()) {
            return bag;
        }
        int sample = rows.stream().mapToInt(r -> r.sampleCount).sum();
        bag.sampleCount = sample;
        bag.recommendCount = rows.stream().mapToInt(r -> r.recommendCount).sum();
        if (sample <= 0) {
            bag.mentionRate = rows.stream().mapToDouble(r -> r.mentionRate).average().orElse(0);
            bag.firstMentionRate = rows.stream().mapToDouble(r -> r.firstMentionRate).average().orElse(0);
            return bag;
        }
        double mention = 0;
        double first = 0;
        for (TrendAgg row : rows) {
            mention += row.mentionRate * row.sampleCount;
            first += row.firstMentionRate * row.sampleCount;
        }
        bag.mentionRate = round(mention / sample);
        bag.firstMentionRate = round(first / sample);
        return bag;
    }

    private static String shiftPeriodKey(String periodKey, boolean weekMode, int offset) {
        if (!StringUtils.hasText(periodKey)) {
            return periodKey;
        }
        if (weekMode) {
            // 2026-W36
            String[] parts = periodKey.split("-W");
            if (parts.length != 2) {
                return periodKey;
            }
            int year = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            LocalDate date = LocalDate.of(year, 1, 4)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                    .plusWeeks(offset);
            return weekKey(date);
        }
        // 2026-09
        String[] parts = periodKey.split("-");
        if (parts.length != 2) {
            return periodKey;
        }
        LocalDate date = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1).plusMonths(offset);
        return monthKey(date);
    }

    private static String monthKey(LocalDate date) {
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    private static Double deltaRate(double current, Double baseline) {
        if (baseline == null) {
            return null;
        }
        return round(current - baseline);
    }

    private static Integer deltaCount(int current, Integer baseline) {
        if (baseline == null) {
            return null;
        }
        return current - baseline;
    }

    private static class MetricBag {
        private double mentionRate;
        private double firstMentionRate;
        private int recommendCount;
        private int sampleCount;
    }

    /**
     * 已结束周期：以落库快照指标为准；未结束周期 / 关键字·话题类型筛选：实时聚合。
     * 快照覆盖同维度 live 行，并补齐 live 已删但快照仍有的历史周期。
     */
    private Map<String, TrendAgg> mergeTrendWithSnapshots(GeoPeriodType type, LocalDate[] range,
                                                          GeoBoardQueryDTO query, List<TrendAgg> live) {
        Map<String, TrendAgg> merged = new LinkedHashMap<>();
        for (TrendAgg row : live) {
            merged.put(rowKey(row), row);
        }
        // 关键字/话题类型筛选口径与快照不一致，不读快照
        if (StringUtils.hasText(query.getKeyword()) || StringUtils.hasText(query.getTermType())) {
            return merged;
        }
        LocalDate today = LocalDate.now();
        for (GeoBoardPeriodStat snap : listSnapshots(type, range[0], range[1], query)) {
            if (snap.getPeriodEnd() == null || !snap.getPeriodEnd().isBefore(today)) {
                continue; // 未结束周期仍用实时
            }
            TrendAgg fromSnap = TrendAgg.fromSnapshot(snap);
            merged.put(rowKey(fromSnap), fromSnap);
        }
        return merged;
    }

    private Map<String, YearAgg> mergeYearlyWithSnapshots(LocalDate[] range, GeoBoardQueryDTO query, List<YearAgg> live) {
        Map<String, YearAgg> merged = new LinkedHashMap<>();
        for (YearAgg row : live) {
            merged.put(rowKey(row), row);
        }
        if (StringUtils.hasText(query.getKeyword()) || StringUtils.hasText(query.getTermType())) {
            return merged;
        }
        LocalDate today = LocalDate.now();
        for (GeoBoardPeriodStat snap : listSnapshots(GeoPeriodType.YEAR, range[0], range[1], query)) {
            boolean unfinished = snap.getPeriodEnd() == null || !snap.getPeriodEnd().isBefore(today);
            // 样例/手工落库的年度指标（含实际达成、达成率）即使周期未结束也优先展示
            boolean hasYearMetrics = snap.getActualRate() != null && snap.getAchieveRate() != null;
            if (unfinished && !hasYearMetrics) {
                continue;
            }
            YearAgg fromSnap = YearAgg.fromSnapshot(snap);
            merged.put(rowKey(fromSnap), fromSnap);
        }
        return merged;
    }

    private Set<String> loadSnapshotPeriodKeys(GeoPeriodType type, LocalDate start, LocalDate end, GeoBoardQueryDTO query) {
        if (StringUtils.hasText(query.getKeyword()) || StringUtils.hasText(query.getTermType())) {
            return Set.of();
        }
        LocalDate today = LocalDate.now();
        Set<String> keys = new HashSet<>();
        for (GeoBoardPeriodStat snap : listSnapshots(type, start, end, query)) {
            if (snap == null || !StringUtils.hasText(snap.getPeriodKey())) {
                continue;
            }
            if (snap.getPeriodEnd() != null && snap.getPeriodEnd().isBefore(today)) {
                keys.add(snap.getPeriodKey());
            }
        }
        return keys;
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
        entity.setTopicName(row.groupName);
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
        entity.setTopicName(row.groupName);
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

    private List<YearAgg> liveYearly(LocalDate start, LocalDate end, GeoBoardQueryDTO query, BoardDim dim) {
        List<GeoMonitorDaily> records = loadActiveDaily(
                start, end, query.getTopicId(), query.getKeyword(), query.getPlatforms(), query.getTermType());
        Map<Long, String> topicNames = topicNameMap();
        List<YearAgg> rows = new ArrayList<>();
        if (dim == BoardDim.OWNER) {
            Map<Long, String> ownerNames = ownerDisplayMap(records);
            Map<String, List<GeoMonitorDaily>> grouped = records.stream()
                    .collect(Collectors.groupingBy(r -> r.getInspectDate().getYear() + "\0" + ownerGroupKey(r) + "\0" + r.getPlatform(),
                            LinkedHashMap::new, Collectors.toList()));
            grouped.forEach((key, list) -> {
                GeoMonitorDaily first = list.getFirst();
                int year = first.getInspectDate().getYear();
                LocalDate ts = LocalDate.of(year, 1, 1);
                LocalDate te = LocalDate.of(year, 12, 31);
                String label = String.valueOf(year);
                rows.add(YearAgg.live(yearPeriodKey(label, ts, te), label, ownerGroupKey(first),
                        ownerDimName(resolveOwnerName(first, ownerNames)),
                        null, first.getPlatform(), ts, te, new BigDecimal("80.00"), list));
            });
            return rows;
        }

        List<GeoYearTarget> targets = targetMapper.selectList(new LambdaQueryWrapper<GeoYearTarget>()
                .eq(query.getTopicId() != null, GeoYearTarget::getTopicId, query.getTopicId())
                .orderByAsc(GeoYearTarget::getSortOrder));
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
                String groupKey = topicGroupKey(target.getTopicId());
                if (byPlatform.isEmpty()) {
                    rows.add(YearAgg.live(periodKey, target.getPeriodLabel(), groupKey, topicName, target.getTopicId(),
                            "-", ts, te, target.getTargetRate(), List.of()));
                } else {
                    byPlatform.forEach((platform, list) -> rows.add(YearAgg.live(
                            periodKey, target.getPeriodLabel(), groupKey, topicName, target.getTopicId(),
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
                rows.add(YearAgg.live(yearPeriodKey(label, ts, te), label, topicGroupKey(first.getTopicId()),
                        topicNames.getOrDefault(first.getTopicId(), ""), first.getTopicId(), first.getPlatform(),
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
        String termType = normalizeTermType(dto.getTermType());
        dto.setTermType(termType);
        assertDailyMutable(dto.getInspectDate(), null);
        GeoMonitorDaily existing = dailyMapper.selectUkIncludeDeleted(dto.getInspectDate(), platform, keyword, termType);
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
            throw new BusinessException("同一天、同一平台、同一关键字、同一话题类型已存在记录");
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
        entity.setTermType(normalizeTermType(dto.getTermType()));
        entity.setPlatform(dto.getPlatform().trim());
        entity.setKeyword(dto.getKeyword().trim());
        applyOwner(entity, dto);
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
                .eq(GeoMonitorDaily::getIsActive, 1)
                .ge(query.getStartDate() != null, GeoMonitorDaily::getInspectDate, query.getStartDate())
                .le(query.getEndDate() != null, GeoMonitorDaily::getInspectDate, query.getEndDate())
                .eq(query.getTopicId() != null, GeoMonitorDaily::getTopicId, query.getTopicId())
                .like(StringUtils.hasText(query.getKeyword()), GeoMonitorDaily::getKeyword, query.getKeyword())
                .eq(StringUtils.hasText(query.getTermType()), GeoMonitorDaily::getTermType, normalizeTermType(query.getTermType()))
                .eq(query.getOwnerUserId() != null, GeoMonitorDaily::getOwnerUserId, query.getOwnerUserId())
                .like(StringUtils.hasText(query.getOwnerName()), GeoMonitorDaily::getOwnerName, query.getOwnerName())
                .in(!platformFilter.isEmpty(), GeoMonitorDaily::getPlatform, platformFilter)
                .eq(query.getMentioned() != null, GeoMonitorDaily::getMentioned, query.getMentioned())
                .ge(query.getRankNoMin() != null, GeoMonitorDaily::getRankNo, query.getRankNoMin())
                .le(query.getRankNoMax() != null, GeoMonitorDaily::getRankNo, query.getRankNoMax())
                .eq(StringUtils.hasText(query.getRecommendStatus()), GeoMonitorDaily::getRecommendStatus, query.getRecommendStatus())
                .like(StringUtils.hasText(query.getCompetitors()), GeoMonitorDaily::getCompetitors, query.getCompetitors())
                .eq(query.getBoardLocked() != null, GeoMonitorDaily::getBoardLocked, query.getBoardLocked())
                .ge(query.getUpdateTimeStart() != null, GeoMonitorDaily::getUpdateTime, query.getUpdateTimeStart())
                .le(query.getUpdateTimeEnd() != null, GeoMonitorDaily::getUpdateTime, query.getUpdateTimeEnd());
        com.base.admin.util.QueryWrappers.applyCreateTimeRange(wrapper, query, GeoMonitorDaily::getCreateTime);
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
            LocalDate start, LocalDate end, Long topicId, String keyword, List<String> platforms, String termType) {
        List<String> platformFilter = platforms == null ? List.of()
                : platforms.stream().filter(StringUtils::hasText).toList();
        // 统计/看板统一只取有效数据；逻辑删除字段再显式约束，防止自定义路径漏过滤
        return new LambdaQueryWrapper<GeoMonitorDaily>()
                .eq(GeoMonitorDaily::getIsActive, 1)
                .ge(start != null, GeoMonitorDaily::getInspectDate, start)
                .le(end != null, GeoMonitorDaily::getInspectDate, end)
                .eq(topicId != null, GeoMonitorDaily::getTopicId, topicId)
                .eq(StringUtils.hasText(termType), GeoMonitorDaily::getTermType, termType)
                .like(StringUtils.hasText(keyword), GeoMonitorDaily::getKeyword, keyword)
                .in(!platformFilter.isEmpty(), GeoMonitorDaily::getPlatform, platformFilter);
    }

    private List<GeoMonitorDaily> loadActiveDaily(LocalDate start, LocalDate end, Long topicId,
                                                  String keyword, List<String> platforms, String termType) {
        return dailyMapper.selectList(buildDailyWrapper(start, end, topicId, keyword, platforms, termType)).stream()
                .filter(GeoMonitorServiceImpl::isActiveDaily)
                .toList();
    }

    private static boolean isActiveDaily(GeoMonitorDaily record) {
        return record != null && !Integer.valueOf(0).equals(record.getIsActive());
    }

    private Map<Long, String> topicNameMap() {
        return topicMapper.selectList(null).stream()
                .collect(Collectors.toMap(GeoTopic::getId, GeoTopic::getTopicName, (a, b) -> a));
    }

    private GeoDailyVO toVo(GeoMonitorDaily e, Map<Long, String> topicNames, Map<Long, String> ownerNames) {
        GeoDailyVO vo = new GeoDailyVO();
        vo.setId(e.getId());
        vo.setInspectDate(e.getInspectDate());
        vo.setTermType(normalizeTermType(e.getTermType()));
        vo.setPlatform(e.getPlatform());
        vo.setKeyword(e.getKeyword());
        vo.setOwnerUserId(e.getOwnerUserId());
        vo.setOwnerName(resolveOwnerName(e, ownerNames));
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

    private void applyOwner(GeoMonitorDaily entity, GeoDailyDTO dto) {
        if (dto.getOwnerUserId() != null) {
            SysUser user = userMapper.selectById(dto.getOwnerUserId());
            if (user == null) {
                throw new BusinessException("负责人用户不存在");
            }
            entity.setOwnerUserId(user.getUserId());
            entity.setOwnerName(userDisplayName(user));
            return;
        }
        entity.setOwnerUserId(null);
        entity.setOwnerName(StringUtils.hasText(dto.getOwnerName()) ? dto.getOwnerName().trim() : null);
    }

    private Map<Long, String> ownerDisplayMap(List<GeoMonitorDaily> records) {
        List<Long> ids = records.stream()
                .map(GeoMonitorDaily::getOwnerUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, ids)).stream()
                .collect(Collectors.toMap(SysUser::getUserId, GeoMonitorServiceImpl::userDisplayName, (a, b) -> a));
    }

    private static String resolveOwnerName(GeoMonitorDaily record, Map<Long, String> ownerNames) {
        if (record == null) {
            return null;
        }
        if (record.getOwnerUserId() != null && ownerNames != null && ownerNames.containsKey(record.getOwnerUserId())) {
            return ownerNames.get(record.getOwnerUserId());
        }
        return StringUtils.hasText(record.getOwnerName()) ? record.getOwnerName().trim() : null;
    }

    private static String userDisplayName(SysUser user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername().trim() : "";
    }

    private static String normalizeTermType(String raw) {
        if (Constants.TERM_TYPE_WEEKLY.equals(raw)) {
            return Constants.TERM_TYPE_WEEKLY;
        }
        return Constants.TERM_TYPE_DAILY;
    }

    private List<DateGroup> parseDateGroups(Sheet sheet, int lastCol) {
        List<Integer> dateCols = new ArrayList<>();
        LocalDate prevDate = null;
        int prevCol = -1;
        for (int col = DATE_START_COL; col < lastCol; col++) {
            LocalDate date = ExcelCellUtils.date(sheet, 0, col);
            if (date == null) {
                continue;
            }
            // 合并单元格会让相邻列读到同一个日期，只记块起点
            if (prevDate != null && date.equals(prevDate) && col == prevCol + 1) {
                prevCol = col;
                continue;
            }
            dateCols.add(col);
            prevDate = date;
            prevCol = col;
        }
        List<DateGroup> groups = new ArrayList<>();
        for (int i = 0; i < dateCols.size(); i++) {
            int start = dateCols.get(i);
            int end = i + 1 < dateCols.size() ? dateCols.get(i + 1) : lastCol;
            int platformCount = (end - start) / METRIC_COUNT;
            if (platformCount < 1) {
                continue;
            }
            DateGroup group = new DateGroup();
            group.date = ExcelCellUtils.date(sheet, 0, start);
            group.startCol = start;
            group.platformCount = platformCount;
            for (int p = 0; p < platformCount; p++) {
                String platform = ExcelCellUtils.str(sheet, 2, start + p);
                if (!StringUtils.hasText(platform)) {
                    continue;
                }
                group.platformIndex.add(p);
                group.platforms.add(platform.trim());
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
                                          BoardDim dim,
                                          Map<Long, String> topicNames) {
        Map<String, List<GeoMonitorDaily>> grouped = records.stream()
                .filter(GeoMonitorServiceImpl::isActiveDaily)
                .collect(Collectors.groupingBy(r -> {
                    String groupKey = dim == BoardDim.OWNER ? ownerGroupKey(r) : topicGroupKey(r.getTopicId());
                    return keyFn.apply(r) + "\0" + groupKey + "\0" + r.getPlatform();
                }, LinkedHashMap::new, Collectors.toList()));
        List<TrendAgg> rows = new ArrayList<>();
        Map<Long, String> ownerNames = ownerDisplayMap(records);
        for (List<GeoMonitorDaily> list : grouped.values()) {
            GeoMonitorDaily first = list.getFirst();
            int total = list.size();
            int mentioned = (int) list.stream().filter(x -> Objects.equals(x.getMentioned(), 1)).count();
            int firstRank = (int) list.stream().filter(x -> Objects.equals(x.getRankNo(), 1)).count();
            int recommend = (int) list.stream().filter(x -> "出现且推荐".equals(x.getRecommendStatus())).count();
            LocalDate[] bounds = boundsFn.apply(first);
            String groupKey;
            String groupName;
            Long topicId;
            if (dim == BoardDim.OWNER) {
                groupKey = ownerGroupKey(first);
                groupName = ownerDimName(resolveOwnerName(first, ownerNames));
                topicId = null;
            } else {
                topicId = first.getTopicId();
                groupKey = topicGroupKey(topicId);
                groupName = topicNames.getOrDefault(topicId, "");
            }
            rows.add(new TrendAgg(
                    keyFn.apply(first),
                    labelFn.apply(first),
                    groupKey,
                    groupName,
                    topicId,
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
        rows.sort(Comparator.comparing(TrendAgg::axis).thenComparing(TrendAgg::groupName).thenComparing(TrendAgg::platform));
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

    /**
     * 三级下钻分组柱：横轴始终=日期。
     * topic：系列=话题；question：系列=目标问题；platform：系列=平台。
     */
    private GeoTopicPlatformChartsVO buildTopicPlatformCharts(List<GeoMonitorDaily> records,
                                                              Map<Long, String> topicNames,
                                                              Long drillTopicId,
                                                              String keyword,
                                                              String grain) {
        GeoTopicPlatformChartsVO vo = new GeoTopicPlatformChartsVO();
        vo.setGrain(grain);
        vo.setTopicId(drillTopicId);
        if (drillTopicId != null) {
            vo.setTopicName(topicNames.getOrDefault(drillTopicId, "话题#" + drillTopicId));
        }
        vo.setKeyword(keyword);

        String seriesMode;
        if (drillTopicId == null) {
            seriesMode = "topic";
            vo.setLevel("topic");
        } else if (!StringUtils.hasText(keyword)) {
            seriesMode = "question";
            vo.setLevel("question");
        } else {
            seriesMode = "platform";
            vo.setLevel("platform");
        }
        vo.setSeriesField(seriesMode);

        Map<String, RankBag> rankBags = new LinkedHashMap<>();
        Map<String, Integer> sampleCnt = new LinkedHashMap<>();
        Map<String, Set<String>> sampleQuestions = new LinkedHashMap<>();
        Map<String, Integer> negativeCnt = new LinkedHashMap<>();
        Map<String, String> seriesLabel = new LinkedHashMap<>();

        for (GeoMonitorDaily r : records) {
            if (drillTopicId != null && !Objects.equals(r.getTopicId(), drillTopicId)) {
                continue;
            }
            String kw = StringUtils.hasText(r.getKeyword()) ? r.getKeyword().trim() : "未填目标问题";
            if (StringUtils.hasText(keyword) && !keyword.equals(kw)) {
                continue;
            }
            if (r.getInspectDate() == null) {
                continue;
            }
            String axis = timeBucket(r.getInspectDate(), grain);
            String platform = StringUtils.hasText(r.getPlatform()) ? r.getPlatform().trim() : "未知平台";
            String seriesKey;
            String seriesName;
            switch (seriesMode) {
                case "question" -> {
                    seriesKey = kw;
                    seriesName = kw;
                }
                case "platform" -> {
                    seriesKey = platform;
                    seriesName = platform;
                }
                default -> {
                    Long tid = r.getTopicId();
                    if (tid == null) {
                        seriesKey = "0";
                        seriesName = "未分话题";
                    } else {
                        seriesKey = String.valueOf(tid);
                        seriesName = topicNames.getOrDefault(tid, "话题#" + tid);
                    }
                }
            }
            String cell = axis + "\0" + seriesKey;
            seriesLabel.put(seriesKey, seriesName);

            sampleCnt.merge(cell, 1, Integer::sum);
            sampleQuestions.computeIfAbsent(cell, k -> new LinkedHashSet<>()).add(kw);
            if (StringUtils.hasText(r.getNegativeContent())) {
                negativeCnt.merge(cell, 1, Integer::sum);
            }
            if (Objects.equals(r.getMentioned(), 1) && r.getRankNo() != null && r.getRankNo() > 0) {
                RankBag bag = rankBags.computeIfAbsent(cell, k -> new RankBag());
                bag.sum += r.getRankNo();
                bag.n++;
            }
        }

        Set<String> cells = new LinkedHashSet<>();
        cells.addAll(sampleCnt.keySet());
        cells.addAll(negativeCnt.keySet());
        cells.addAll(rankBags.keySet());
        for (String cell : cells) {
            String[] parts = cell.split("\0", 2);
            String axis = parts[0];
            String sKey = parts.length > 1 ? parts[1] : "-";
            String series = seriesLabel.getOrDefault(sKey, sKey);
            RankBag rb = rankBags.get(cell);
            if (rb != null && rb.n > 0) {
                addChart(vo.getRankChart(), axis, series, (double) rb.sum / rb.n, sKey);
            }
            if ("platform".equals(seriesMode)) {
                Integer sc = sampleCnt.get(cell);
                if (sc != null && sc > 0) {
                    addChart(vo.getSampleChart(), axis, series, sc, sKey);
                }
            } else {
                Set<String> qs = sampleQuestions.get(cell);
                if (qs != null && !qs.isEmpty()) {
                    addChart(vo.getSampleChart(), axis, series, qs.size(), sKey);
                }
            }
            Integer nc = negativeCnt.get(cell);
            if (nc != null && nc > 0) {
                addChart(vo.getNegativeChart(), axis, series, nc, sKey);
            }
        }
        sortChart(vo.getRankChart());
        sortChart(vo.getSampleChart());
        sortChart(vo.getNegativeChart());
        return vo;
    }

    private static String timeBucket(LocalDate date, String grain) {
        if (date == null) {
            return "-";
        }
        return switch (grain == null ? "day" : grain) {
            case "month" -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            case "year" -> String.valueOf(date.getYear());
            case "week" -> {
                java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.of(Locale.CHINA);
                int w = date.get(wf.weekOfWeekBasedYear());
                int y = date.get(wf.weekBasedYear());
                yield y + "-W" + String.format("%02d", w);
            }
            default -> date.toString();
        };
    }

    private static final class RankBag {
        long sum;
        int n;
    }

    private void fillNegativeSummary(List<GeoMonitorDaily> records, Map<Long, String> topicNames, GeoDailyBoardVO board) {
        Map<String, GeoNegativeSummaryRowVO> map = new LinkedHashMap<>();
        int total = 0;
        for (GeoMonitorDaily r : records) {
            if (!StringUtils.hasText(r.getNegativeContent())) {
                continue;
            }
            total++;
            String key = (r.getInspectDate() == null ? "-" : r.getInspectDate().toString())
                    + "\0" + r.getTopicId() + "\0" + r.getPlatform() + "\0" + normalizeTermType(r.getTermType());
            GeoNegativeSummaryRowVO row = map.computeIfAbsent(key, k -> {
                GeoNegativeSummaryRowVO vo = new GeoNegativeSummaryRowVO();
                vo.setInspectDate(r.getInspectDate() == null ? null : r.getInspectDate().toString());
                vo.setTopicId(r.getTopicId());
                vo.setTopicName(topicNames.getOrDefault(r.getTopicId(), ""));
                vo.setPlatform(r.getPlatform());
                vo.setTermType(normalizeTermType(r.getTermType()));
                vo.setNegativeCount(0);
                return vo;
            });
            row.setNegativeCount(row.getNegativeCount() + 1);
        }
        board.setNegativeCount(total);
        board.getNegativeRows().addAll(map.values());
    }

    /** 日报筛选区间 vs 等长上一区间（环比）/ 去年同区间（同比） */
    private GeoBoardCompareSummaryVO buildDailyPeriodCompare(LocalDate[] range, GeoBoardQueryDTO query) {
        GeoBoardCompareSummaryVO summary = new GeoBoardCompareSummaryVO();
        summary.setCompareHint("环比=等长上一区间；同比=去年同区间");
        MetricBag current = metricsOfRecords(loadActiveDaily(
                range[0], range[1], query.getTopicId(), query.getKeyword(), query.getPlatforms(), query.getTermType()));
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(range[0], range[1]) + 1);
        LocalDate momEnd = range[0].minusDays(1);
        LocalDate momStart = momEnd.minusDays(days - 1);
        MetricBag mom = metricsOfRecords(loadActiveDaily(
                momStart, momEnd, query.getTopicId(), query.getKeyword(), query.getPlatforms(), query.getTermType()));
        LocalDate yoyStart = range[0].minusYears(1);
        LocalDate yoyEnd = range[1].minusYears(1);
        MetricBag yoy = metricsOfRecords(loadActiveDaily(
                yoyStart, yoyEnd, query.getTopicId(), query.getKeyword(), query.getPlatforms(), query.getTermType()));
        summary.setMentionRate(current.mentionRate);
        summary.setFirstMentionRate(current.firstMentionRate);
        summary.setRecommendCount(current.recommendCount);
        summary.setSampleCount(current.sampleCount);
        summary.setMentionRateMom(mom.sampleCount == 0 ? null : deltaRate(current.mentionRate, Double.valueOf(mom.mentionRate)));
        summary.setMentionRateYoy(yoy.sampleCount == 0 ? null : deltaRate(current.mentionRate, Double.valueOf(yoy.mentionRate)));
        summary.setFirstMentionRateMom(mom.sampleCount == 0 ? null : deltaRate(current.firstMentionRate, Double.valueOf(mom.firstMentionRate)));
        summary.setFirstMentionRateYoy(yoy.sampleCount == 0 ? null : deltaRate(current.firstMentionRate, Double.valueOf(yoy.firstMentionRate)));
        summary.setRecommendCountMom(mom.sampleCount == 0 ? null : deltaCount(current.recommendCount, Integer.valueOf(mom.recommendCount)));
        summary.setRecommendCountYoy(yoy.sampleCount == 0 ? null : deltaCount(current.recommendCount, Integer.valueOf(yoy.recommendCount)));
        return summary;
    }

    private static MetricBag metricsOfRecords(List<GeoMonitorDaily> records) {
        MetricBag bag = new MetricBag();
        if (records == null || records.isEmpty()) {
            return bag;
        }
        int sample = records.size();
        int mentioned = 0;
        int first = 0;
        int recommend = 0;
        for (GeoMonitorDaily r : records) {
            if (Integer.valueOf(1).equals(r.getMentioned())) {
                mentioned++;
                if (r.getRankNo() != null && r.getRankNo() == 1) {
                    first++;
                }
            }
            if ("出现且推荐".equals(r.getRecommendStatus())) {
                recommend++;
            }
        }
        bag.sampleCount = sample;
        bag.recommendCount = recommend;
        bag.mentionRate = pct(mentioned, sample);
        bag.firstMentionRate = pct(first, mentioned);
        return bag;
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
                .filter(GeoMonitorServiceImpl::isActiveDaily)
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
        return monthKey(record.getInspectDate());
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

    private enum BoardDim {
        TOPIC, OWNER
    }

    private static String topicGroupKey(Long topicId) {
        return "t:" + (topicId == null ? "0" : topicId);
    }

    private static String ownerGroupKey(GeoMonitorDaily record) {
        return "o:" + ownerDimKey(record);
    }

    private static String ownerDimKey(GeoMonitorDaily record) {
        if (record == null) {
            return "";
        }
        if (record.getOwnerUserId() != null) {
            return "u:" + record.getOwnerUserId();
        }
        if (StringUtils.hasText(record.getOwnerName())) {
            return "n:" + record.getOwnerName().trim();
        }
        return "";
    }

    private static String ownerDimName(String ownerName) {
        return StringUtils.hasText(ownerName) ? ownerName : "未指定";
    }

    private static String rowKey(String periodKey, String groupKey, String platform) {
        return periodKey + "\0" + groupKey + "\0" + platform;
    }

    private static String rowKey(TrendAgg row) {
        return rowKey(row.periodKey(), row.groupKey(), row.platform());
    }

    private static String rowKey(YearAgg row) {
        return rowKey(row.periodKey(), row.groupKey(), row.platform());
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
        addChart(chart, axis, series, value, null);
    }

    private static void addChart(List<GeoChartPointVO> chart, String axis, String series, double value, String key) {
        GeoChartPointVO p = new GeoChartPointVO();
        p.setAxis(axis);
        p.setSeries(series);
        p.setValue(round(value));
        p.setKey(key);
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
        private int platformCount;
        private final List<Integer> platformIndex = new ArrayList<>();
        private final List<String> platforms = new ArrayList<>();
    }

    private record TrendAgg(String periodKey, String axis, String groupKey, String groupName, Long topicId,
                            String platform, LocalDate periodStart, LocalDate periodEnd, int sampleCount,
                            double mentionRate, double firstMentionRate, int recommendCount, String competitorTop,
                            String citePlatformTop, boolean fromSnapshot) {
        static TrendAgg fromSnapshot(GeoBoardPeriodStat snap) {
            Long topicId = snap.getTopicId();
            return new TrendAgg(
                    snap.getPeriodKey(),
                    snap.getPeriodLabel(),
                    topicGroupKey(topicId),
                    snap.getTopicName(),
                    topicId,
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

    private record YearAgg(String periodKey, String periodLabel, String groupKey, String groupName, Long topicId,
                           String platform, LocalDate periodStart, LocalDate periodEnd, BigDecimal targetRate,
                           double actualRate, double achieveRate, int sampleCount, boolean fromSnapshot) {
        static YearAgg live(String periodKey, String periodLabel, String groupKey, String groupName, Long topicId,
                            String platform, LocalDate periodStart, LocalDate periodEnd, BigDecimal targetRate,
                            List<GeoMonitorDaily> list) {
            List<GeoMonitorDaily> active = list == null ? List.of() : list.stream()
                    .filter(GeoMonitorServiceImpl::isActiveDaily)
                    .toList();
            int total = active.size();
            int mentioned = (int) active.stream().filter(x -> Objects.equals(x.getMentioned(), 1)).count();
            double actual = pct(mentioned, total);
            double achieve = targetRate == null || targetRate.doubleValue() == 0 ? 0 : actual / targetRate.doubleValue() * 100;
            return new YearAgg(periodKey, periodLabel, groupKey, groupName, topicId, platform, periodStart, periodEnd,
                    targetRate, round(actual), round(achieve), total, false);
        }

        static YearAgg fromSnapshot(GeoBoardPeriodStat snap) {
            Long topicId = snap.getTopicId();
            return new YearAgg(
                    snap.getPeriodKey(),
                    snap.getPeriodLabel(),
                    topicGroupKey(topicId),
                    snap.getTopicName(),
                    topicId,
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
