package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.Constants;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentAiGenerateDTO;
import com.base.admin.domain.dto.GeoContentArticleBoardQueryDTO;
import com.base.admin.domain.dto.GeoContentArticleDetailQueryDTO;
import com.base.admin.domain.dto.GeoContentDraftSaveDTO;
import com.base.admin.domain.dto.GeoContentItemDraftSaveDTO;
import com.base.admin.domain.dto.GeoContentOpinionSaveDTO;
import com.base.admin.domain.dto.GeoContentPlatformArticleDTO;
import com.base.admin.domain.dto.GeoContentReviewActionDTO;
import com.base.admin.domain.dto.GeoContentPlacementArticleQueryDTO;
import com.base.admin.domain.dto.GeoContentPlacementCiteDTO;
import com.base.admin.domain.dto.GeoContentPlacementDTO;
import com.base.admin.domain.dto.GeoContentPlacementItemDTO;
import com.base.admin.domain.dto.GeoContentPlacementQueryDTO;
import com.base.admin.domain.dto.GeoContentPublisherWeekDetailQueryDTO;
import com.base.admin.domain.dto.GeoContentPublisherWeekQueryDTO;
import com.base.admin.domain.dto.GeoSimilarQuestionCommitDTO;
import com.base.admin.domain.entity.GeoContentPeriodStat;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoContentReview;
import com.base.admin.domain.entity.GeoContentPlacementCite;
import com.base.admin.domain.entity.GeoContentPlacementItem;
import com.base.admin.domain.entity.GeoContentReviewNote;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskFile;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.GeoArticlePipelineResultVO;
import com.base.admin.domain.vo.GeoContentAiGenerateVO;
import com.base.admin.domain.vo.GeoChartPointVO;
import com.base.admin.domain.vo.GeoSellPointContextVO;
import com.base.admin.domain.vo.GeoContentArticleBoardVO;
import com.base.admin.domain.vo.GeoContentArticleDetailRowVO;
import com.base.admin.domain.vo.GeoContentCiteAggRowVO;
import com.base.admin.domain.vo.GeoContentArticleBundleVO;
import com.base.admin.domain.vo.GeoContentArticleVO;
import com.base.admin.domain.vo.GeoContentDraftVO;
import com.base.admin.domain.vo.GeoContentOpinionVO;
import com.base.admin.domain.vo.GeoContentPlacementArticleListVO;
import com.base.admin.domain.vo.GeoContentPlacementCiteVO;
import com.base.admin.domain.vo.GeoContentPlacementDetailVO;
import com.base.admin.domain.vo.GeoContentPlacementItemVO;
import com.base.admin.domain.vo.GeoContentPlacementListVO;
import com.base.admin.domain.vo.GeoTargetQuestionOptionVO;
import com.base.admin.domain.vo.GeoContentPublishAggRowVO;
import com.base.admin.domain.vo.GeoContentPublisherCiteRowVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekBoardVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekDetailVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekRowVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoPersistResultVO;
import com.base.admin.domain.vo.GeoRankItemVO;
import com.base.admin.domain.vo.GeoSimilarQuestionPreviewVO;
import com.base.admin.domain.vo.SysTaskFileVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPeriodStatMapper;
import com.base.admin.mapper.GeoContentPlacementCiteMapper;
import com.base.admin.mapper.GeoContentPlacementItemMapper;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.GeoContentReviewMapper;
import com.base.admin.mapper.GeoContentReviewNoteMapper;
import com.base.admin.mapper.SysTaskFileMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.AiChatService;
import com.base.admin.service.DataScopeFilter;
import com.base.admin.service.FileStorageService;
import com.base.admin.service.GeoArticlePipelineService;
import com.base.admin.service.GeoContentPlacementService;
import com.base.admin.service.GeoContentPublisherRuleService;
import com.base.admin.service.GeoTopicService;
import com.base.admin.service.PlacementTaskSyncService;
import com.base.admin.service.SellPointContextService;
import com.base.admin.service.SysAiProviderService;
import com.base.admin.service.SysTaskService;
import com.base.admin.util.ExcelCellUtils;
import com.base.admin.util.GeoExcelTemplateWriter;
import com.base.admin.util.GeoImportProgress;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoContentPlacementServiceImpl implements GeoContentPlacementService {

    private static final int CITE_START_COL = 10;
    private final ReentrantLock importLock = new ReentrantLock();

    private final GeoContentPlacementMapper placementMapper;
    private final GeoContentPlacementItemMapper itemMapper;
    private final GeoContentPlacementCiteMapper citeMapper;
    private final GeoContentReviewMapper contentReviewMapper;
    private final GeoContentReviewNoteMapper reviewNoteMapper;
    private final GeoContentPeriodStatMapper contentPeriodStatMapper;
    private final SysUserMapper userMapper;
    private final GeoTopicService topicService;
    private final AiChatService aiChatService;
    private final SysAiProviderService sysAiProviderService;
    private final SellPointContextService sellPointContextService;
    private final GeoArticlePipelineService articlePipelineService;
    private final PlacementTaskSyncService placementTaskSyncService;
    private final GeoContentPublisherRuleService publisherRuleService;
    private final ObjectMapper objectMapper;
    private final SysTaskMapper taskMapper;
    private final SysTaskFileMapper taskFileMapper;
    private final FileStorageService fileStorageService;
    private final DataScopeFilter dataScopeFilter;
    private final ObjectProvider<SysTaskService> taskServiceProvider;

    @Override
    public PageResult<GeoContentPlacementListVO> list(GeoContentPlacementQueryDTO query) {
        String filterAgg = normalizeProgressFilter(query.getAggregateStatus());
        LambdaQueryWrapper<GeoContentPlacement> wrapper = new LambdaQueryWrapper<GeoContentPlacement>()
                .eq(query.getPublisherUserId() != null, GeoContentPlacement::getPublisherUserId, query.getPublisherUserId())
                .eq(query.getOwnerUserId() != null, GeoContentPlacement::getOwnerUserId, query.getOwnerUserId())
                .eq(query.getTopicId() != null, GeoContentPlacement::getTopicId, query.getTopicId())
                .like(StringUtils.hasText(query.getTargetQuestion()), GeoContentPlacement::getTargetQuestion, query.getTargetQuestion())
                .like(StringUtils.hasText(query.getTitle()), GeoContentPlacement::getTitle, query.getTitle())
                .eq(StringUtils.hasText(query.getSource()), GeoContentPlacement::getSource, query.getSource())
                .and(query.getRelatedUserId() != null, w -> w
                        .eq(GeoContentPlacement::getPublisherUserId, query.getRelatedUserId())
                        .or()
                        .eq(GeoContentPlacement::getOwnerUserId, query.getRelatedUserId()));
        if (Constants.CONTENT_AGG_PENDING.equals(filterAgg)) {
            wrapper.and(w -> w.eq(GeoContentPlacement::getPlacementProgress, Constants.CONTENT_AGG_PENDING)
                    .or(sub -> sub.eq(GeoContentPlacement::getPlacementProgress, Constants.CONTENT_AGG_NONE)
                            .inSql(GeoContentPlacement::getId, pendingProofPlacementSql())));
        } else if (StringUtils.hasText(filterAgg)) {
            wrapper.eq(GeoContentPlacement::getPlacementProgress, filterAgg);
        }
        wrapper.orderByDesc(GeoContentPlacement::getId);
        com.base.admin.util.QueryWrappers.applyCreateTimeRange(wrapper, query, GeoContentPlacement::getCreateTime);
        dataScopeFilter.applyGeoPlacement(wrapper);

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        Page<GeoContentPlacement> page = placementMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<GeoContentPlacement> candidates = page.getRecords();
        if (candidates.isEmpty()) {
            return new PageResult<>(0, List.of());
        }

        List<Long> ids = candidates.stream().map(GeoContentPlacement::getId).toList();
        Map<Long, List<GeoContentPlacementItem>> itemsByPlacement = itemMapper.selectList(
                        new LambdaQueryWrapper<GeoContentPlacementItem>()
                                .in(GeoContentPlacementItem::getPlacementId, ids)
                                .orderByAsc(GeoContentPlacementItem::getSortOrder)
                                .orderByAsc(GeoContentPlacementItem::getId))
                .stream()
                .collect(Collectors.groupingBy(GeoContentPlacementItem::getPlacementId));

        Map<Long, Long> citeCountMap = citeMapper.selectList(
                        new LambdaQueryWrapper<GeoContentPlacementCite>()
                                .in(GeoContentPlacementCite::getPlacementId, ids)
                                .select(GeoContentPlacementCite::getId, GeoContentPlacementCite::getPlacementId))
                .stream()
                .collect(Collectors.groupingBy(GeoContentPlacementCite::getPlacementId, Collectors.counting()));

        Map<Long, Integer> proofCountMap = loadProofFileCounts(ids);

        Set<Long> sourceIds = candidates.stream()
                .map(GeoContentPlacement::getSourcePlacementId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> sourceQuestionMap = sourceIds.isEmpty() ? Map.of()
                : placementMapper.selectBatchIds(sourceIds).stream()
                .collect(Collectors.toMap(GeoContentPlacement::getId, GeoContentPlacement::getTargetQuestion, (a, b) -> a));

        List<GeoContentPlacementListVO> rows = new ArrayList<>();
        for (GeoContentPlacement entity : candidates) {
            List<GeoContentPlacementItem> items = itemsByPlacement.getOrDefault(entity.getId(), List.of());
            GeoContentPlacementListVO vo = toListVo(entity, items, citeCountMap.getOrDefault(entity.getId(), 0L).intValue());
            vo.setProofFileCount(proofCountMap.getOrDefault(entity.getId(), 0));
            applyPendingIfNeeded(vo);
            if (entity.getSourcePlacementId() != null) {
                vo.setSourceTargetQuestion(sourceQuestionMap.get(entity.getSourcePlacementId()));
            }
            rows.add(vo);
        }
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public PageResult<GeoContentPlacementArticleListVO> listArticles(GeoContentPlacementArticleQueryDTO query) {
        LambdaQueryWrapper<GeoContentPlacement> pw = new LambdaQueryWrapper<GeoContentPlacement>()
                .eq(query.getPlacementId() != null, GeoContentPlacement::getId, query.getPlacementId())
                .eq(query.getPublisherUserId() != null, GeoContentPlacement::getPublisherUserId, query.getPublisherUserId())
                .eq(query.getOwnerUserId() != null, GeoContentPlacement::getOwnerUserId, query.getOwnerUserId())
                .eq(query.getTopicId() != null, GeoContentPlacement::getTopicId, query.getTopicId())
                .like(StringUtils.hasText(query.getTargetQuestion()), GeoContentPlacement::getTargetQuestion, query.getTargetQuestion())
                .select(GeoContentPlacement::getId);
        dataScopeFilter.applyGeoPlacement(pw);
        List<GeoContentPlacement> placements = placementMapper.selectList(pw);
        if (placements.isEmpty()) {
            return new PageResult<>(0, List.of());
        }
        List<Long> placementIds = placements.stream().map(GeoContentPlacement::getId).toList();
        Map<Long, GeoContentPlacement> placementMap = Map.of();

        String statusFilter = StringUtils.hasText(query.getPublishStatus())
                ? normalizePublishStatus(query.getPublishStatus())
                : null;
        String platformName = StringUtils.hasText(query.getPlatformName()) ? query.getPlatformName().trim() : null;
        LambdaQueryWrapper<GeoContentPlacementItem> iw = new LambdaQueryWrapper<GeoContentPlacementItem>()
                .in(GeoContentPlacementItem::getPlacementId, placementIds)
                .like(StringUtils.hasText(query.getTitle()), GeoContentPlacementItem::getTitle, query.getTitle())
                .eq(platformName != null, GeoContentPlacementItem::getPlatformName, platformName)
                .eq(StringUtils.hasText(statusFilter), GeoContentPlacementItem::getPublishStatus, statusFilter);
        var snap = dataScopeFilter.snapshot();
        // 文章列表只展示已选发布平台的明细；审核通过的空占位不再出现在这里
        iw.and(w -> w.isNotNull(GeoContentPlacementItem::getPlatformName)
                .ne(GeoContentPlacementItem::getPlatformName, ""));
        if (!snap.globalAll() && snap.geoEnabled() && !snap.geoPlatformNames().isEmpty()) {
            iw.in(GeoContentPlacementItem::getPlatformName, snap.geoPlatformNames());
        }
        if (query.getPublishTimeStart() != null) {
            iw.ge(GeoContentPlacementItem::getPublishTime, query.getPublishTimeStart().atStartOfDay());
        }
        if (query.getPublishTimeEnd() != null) {
            iw.lt(GeoContentPlacementItem::getPublishTime, query.getPublishTimeEnd().plusDays(1).atStartOfDay());
        }
        if (query.getCited() != null && query.getCited() == 1) {
            iw.apply(citeExistsSql());
        } else if (query.getCited() != null && query.getCited() == 0) {
            iw.apply("NOT " + citeExistsSql());
        }
        com.base.admin.util.QueryWrappers.applyCreateTimeRange(iw, query, GeoContentPlacementItem::getCreateTime);
        String citeSort = query.getCiteSort() == null ? "" : query.getCiteSort().trim().toLowerCase();
        if ("asc".equals(citeSort) || "desc".equals(citeSort)) {
            iw.last("ORDER BY (SELECT COUNT(1) FROM geo_content_placement_cite c WHERE c.item_id = geo_content_placement_item.id AND c.is_active = 1) "
                    + citeSort.toUpperCase() + ", geo_content_placement_item.id DESC");
        } else {
            iw.orderByDesc(GeoContentPlacementItem::getId);
        }

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        Page<GeoContentPlacementItem> page = itemMapper.selectPage(new Page<>(pageNum, pageSize), iw);
        List<GeoContentPlacementItem> items = page.getRecords();
        if (items.isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }

        Set<Long> loadPlacementIds = items.stream()
                .map(GeoContentPlacementItem::getPlacementId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!loadPlacementIds.isEmpty()) {
            placementMap = placementMapper.selectBatchIds(loadPlacementIds).stream()
                    .collect(Collectors.toMap(GeoContentPlacement::getId, p -> p, (a, b) -> a));
        }

        Map<Long, Integer> citeCountByItem = new HashMap<>();
        List<Long> itemIds = items.stream().map(GeoContentPlacementItem::getId).filter(Objects::nonNull).toList();
        if (!itemIds.isEmpty()) {
            citeMapper.selectList(new LambdaQueryWrapper<GeoContentPlacementCite>()
                            .in(GeoContentPlacementCite::getItemId, itemIds)
                            .select(GeoContentPlacementCite::getId, GeoContentPlacementCite::getItemId))
                    .forEach(cite -> {
                        if (cite.getItemId() != null) {
                            citeCountByItem.merge(cite.getItemId(), 1, Integer::sum);
                        }
                    });
        }

        List<GeoContentPlacementArticleListVO> rows = new ArrayList<>();
        for (GeoContentPlacementItem item : items) {
            GeoContentPlacement placement = placementMap.get(item.getPlacementId());
            rows.add(toArticleListVo(item, placement, citeCountByItem.getOrDefault(item.getId(), 0)));
        }
        return new PageResult<>(page.getTotal(), rows);
    }

    private static String citeExistsSql() {
        return "EXISTS (SELECT 1 FROM geo_content_placement_cite c WHERE c.item_id = geo_content_placement_item.id AND c.is_active = 1)";
    }

    @Override
    public List<GeoTargetQuestionOptionVO> listTargetQuestions(Long topicId) {
        if (topicId == null) {
            return List.of();
        }
        LambdaQueryWrapper<GeoContentPlacement> w = new LambdaQueryWrapper<GeoContentPlacement>()
                .eq(GeoContentPlacement::getTopicId, topicId)
                .isNotNull(GeoContentPlacement::getTargetQuestion)
                .ne(GeoContentPlacement::getTargetQuestion, "")
                .orderByDesc(GeoContentPlacement::getId)
                .select(GeoContentPlacement::getId, GeoContentPlacement::getTargetQuestion);
        dataScopeFilter.applyGeoPlacement(w);
        return placementMapper.selectList(w)
                .stream()
                .map(p -> {
                    GeoTargetQuestionOptionVO vo = new GeoTargetQuestionOptionVO();
                    vo.setPlacementId(p.getId());
                    vo.setTargetQuestion(p.getTargetQuestion());
                    return vo;
                })
                .toList();
    }

    @Override
    public GeoContentPlacementDetailVO getDetail(Long id) {
        GeoContentPlacement entity = requirePlacement(id);
        List<GeoContentPlacementItemVO> items = listItems(id);
        List<GeoContentPlacementCiteVO> cites = listCites(id, null);

        GeoContentPlacementDetailVO detail = new GeoContentPlacementDetailVO();
        detail.setId(entity.getId());
        detail.setPublisherUserId(entity.getPublisherUserId());
        detail.setPublisherName(entity.getPublisherName());
        detail.setOwnerUserId(entity.getOwnerUserId());
        detail.setOwnerName(entity.getOwnerName());
        detail.setTopicId(entity.getTopicId());
        detail.setTopicName(entity.getTopicName());
        detail.setTargetQuestion(entity.getTargetQuestion());
        detail.setTitle(resolveDisplayTitle(entity, items));
        detail.setSource(entity.getSource());
        detail.setSourcePlacementId(entity.getSourcePlacementId());
        detail.setSourceAiModel(entity.getSourceAiModel());
        if (entity.getSourcePlacementId() != null) {
            GeoContentPlacement source = placementMapper.selectById(entity.getSourcePlacementId());
            if (source != null) {
                detail.setSourceTargetQuestion(source.getTargetQuestion());
            }
        }
        detail.setRemark(entity.getRemark());
        detail.setItems(items);
        detail.setCites(cites);
        detail.setProofFiles(listProofFiles(id));
        String progress = StringUtils.hasText(entity.getPlacementProgress())
                ? entity.getPlacementProgress()
                : aggregateStatus(items.stream().map(GeoContentPlacementItemVO::getPublishStatus).toList());
        detail.setAggregateStatus(progress);
        detail.setPublishProgress(publishProgress(items.size(),
                (int) items.stream().filter(i -> Constants.CONTENT_PUBLISH_SUCCESS.equals(i.getPublishStatus())).count()));
        return detail;
    }

    @Override
    public GeoContentDraftVO getDraft(Long placementId) {
        GeoContentPlacement placement = requirePlacement(placementId);
        return toDraftVo(placement);
    }

    @Override
    @Transactional
    public GeoContentDraftVO saveDraft(GeoContentDraftSaveDTO dto) {
        if (dto == null || dto.getPlacementId() == null) {
            throw new BusinessException("内容投放ID不能为空");
        }
        GeoContentPlacement placement = requirePlacement(dto.getPlacementId());
        String lockStatus = placement.getContentStatus();
        if (Constants.CONTENT_STATUS_QUEUE.equals(lockStatus)
                || Constants.CONTENT_STATUS_PUBLISHED.equals(lockStatus)) {
            throw new BusinessException("待投放/已发布文章请到「投放管理」维护投放，不可再改稿");
        }
        if (Constants.CONTENT_STATUS_PENDING_REVIEW.equals(lockStatus)
                && hasPendingContentReview(placement.getId())) {
            throw new BusinessException("审核中不可修改正文");
        }
        long currentVersion = placement.getContentVersion() == null ? 0L : placement.getContentVersion();
        Long clientVersion = dto.getContentVersion();
        String nextTitle = dto.getContentTitle() == null ? "" : dto.getContentTitle().trim();
        String nextBody = dto.getContentBody() == null ? "" : dto.getContentBody();
        String prevTitle = placement.getContentTitle() == null ? "" : placement.getContentTitle();
        String prevBody = placement.getContentBody() == null ? "" : placement.getContentBody();
        boolean changed = !nextTitle.equals(prevTitle) || !nextBody.equals(prevBody);
        if (clientVersion != null && clientVersion != currentVersion) {
            if (!changed) {
                return toDraftVo(placement);
            }
            throw new BusinessException("正文已在别处保存，请刷新后重试（版本冲突）");
        }
        if (!changed) {
            return toDraftVo(placement);
        }
        String nextStatus = lockStatus;
        if (!StringUtils.hasText(nextStatus)
                || (Constants.CONTENT_STATUS_PENDING_REVIEW.equals(nextStatus) && !hasPendingContentReview(placement.getId()))) {
            nextStatus = Constants.CONTENT_STATUS_DRAFTING;
        }
        long nextVersion = currentVersion + 1;
        LocalDateTime now = LocalDateTime.now();
        String editor = SecurityUtils.getCurrentUsername();
        // 显式 SET LONGTEXT，避免 updateById 在个别环境下漏写正文
        placementMapper.update(null, new LambdaUpdateWrapper<GeoContentPlacement>()
                .eq(GeoContentPlacement::getId, placement.getId())
                .set(GeoContentPlacement::getContentTitle, nextTitle)
                // 与撰写记录/投放维护列表标题对齐：投放标题跟正文标题走
                .set(GeoContentPlacement::getTitle, nextTitle)
                .set(GeoContentPlacement::getContentBody, nextBody)
                .set(GeoContentPlacement::getContentFormat, Constants.CONTENT_FORMAT_LAKE)
                .set(GeoContentPlacement::getContentStatus, nextStatus)
                .set(GeoContentPlacement::getContentVersion, nextVersion)
                .set(GeoContentPlacement::getContentUpdatedBy, editor)
                .set(GeoContentPlacement::getContentUpdatedAt, now));
        GeoContentPlacement saved = requirePlacement(placement.getId());
        String savedBody = saved.getContentBody() == null ? "" : saved.getContentBody();
        if (!nextBody.equals(savedBody)) {
            log.warn("统一稿正文回读不一致 placementId={} sentLen={} savedLen={}",
                    placement.getId(), nextBody.length(), savedBody.length());
            placementMapper.update(null, new LambdaUpdateWrapper<GeoContentPlacement>()
                    .eq(GeoContentPlacement::getId, placement.getId())
                    .set(GeoContentPlacement::getContentBody, nextBody));
            saved = requirePlacement(placement.getId());
            savedBody = saved.getContentBody() == null ? "" : saved.getContentBody();
            if (!nextBody.equals(savedBody)) {
                throw new BusinessException("正文保存失败，请重试");
            }
        }
        // 统一稿：仅把「仍为空 / 仍等于旧正文标题」的平台行标题跟上，不覆盖维护投放手填标题
        syncSharedItemTitles(placement.getId(), nextTitle, prevTitle);
        if (changed) {
            markSharedArticlesNeedRevision(placement.getId());
        }
        if (StringUtils.hasText(nextTitle) || StringUtils.hasText(nextBody)) {
            SysTaskService taskService = taskServiceProvider.getIfAvailable();
            if (taskService != null) {
                taskService.markGeoWriteDrafted(placement.getId());
            }
        }
        return toDraftVo(saved);
    }

    @Override
    public GeoContentArticleBundleVO getArticleBundle(Long placementId) {
        GeoContentPlacement placement = requirePlacement(placementId);
        List<GeoContentPlacementItem> items = listPlacementItems(placementId);
        Map<Long, List<GeoContentOpinionVO>> opinions = loadOpinions(placementId);
        GeoContentArticleBundleVO bundle = new GeoContentArticleBundleVO();
        bundle.setPlacementId(placement.getId());
        bundle.setTopicName(placement.getTopicName());
        bundle.setTargetQuestion(placement.getTargetQuestion());
        bundle.setShared(toDraftVo(placement));
        bundle.setArticles(items.stream().map(item -> toArticleVo(placement, item, opinions)).toList());
        if (StringUtils.hasText(placement.getContentTitle()) || StringUtils.hasText(placement.getContentBody())) {
            SysTaskService taskService = taskServiceProvider.getIfAvailable();
            if (taskService != null) {
                taskService.markGeoWriteDrafted(placement.getId());
            }
        }
        return bundle;
    }

    @Override
    @Transactional
    public GeoContentArticleVO addPlatformArticle(GeoContentPlatformArticleDTO dto) {
        if (dto == null || dto.getPlacementId() == null) {
            throw new BusinessException("内容投放ID不能为空");
        }
        GeoContentPlacement placement = requirePlacement(dto.getPlacementId());
        String platformName = nz(dto.getPlatformName());
        if (!StringUtils.hasText(platformName)) {
            throw new BusinessException("请选择内容发布平台");
        }
        Long exists = itemMapper.selectCount(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .eq(GeoContentPlacementItem::getPlacementId, placement.getId())
                .eq(GeoContentPlacementItem::getPlatformName, platformName));
        if (exists != null && exists > 0) {
            throw new BusinessException("该问题已有「" + platformName + "」上的文章");
        }
        GeoContentPlacementItem item = new GeoContentPlacementItem();
        item.setPlacementId(placement.getId());
        item.setPlatformName(platformName);
        item.setContentForm(normalizeContentForm(dto.getContentForm(), platformName));
        String title = StringUtils.hasText(placement.getContentTitle())
                ? placement.getContentTitle().trim()
                : nz(placement.getTargetQuestion());
        item.setTitle(StringUtils.hasText(title) ? title : platformName);
        item.setPublishStatus(Constants.CONTENT_PUBLISH_NONE);
        item.setContentMode(Constants.CONTENT_MODE_SHARED);
        item.setContentStatus(Constants.CONTENT_STATUS_DRAFTING);
        item.setContentFormat(Constants.CONTENT_FORMAT_LAKE);
        item.setContentVersion(0L);
        item.setSortOrder(nextItemSort(placement.getId()));
        itemMapper.insert(item);
        refreshPlacementContentRollup(placement.getId());
        syncPlacementDerived(placement.getId());
        return toArticleVo(requirePlacement(placement.getId()), requireItem(item.getId()), loadOpinions(placement.getId()));
    }

    @Override
    @Transactional
    public GeoContentArticleVO saveItemDraft(GeoContentItemDraftSaveDTO dto) {
        if (dto == null || dto.getItemId() == null) {
            throw new BusinessException("平台文章ID不能为空");
        }
        GeoContentPlacementItem item = requireItem(dto.getItemId());
        GeoContentPlacement placement = requirePlacement(item.getPlacementId());
        String mode = normalizeContentMode(dto.getContentMode());
        String status = articleStatus(item);
        if (!articleEditable(status)) {
            throw new BusinessException("当前审核状态不可修改这篇稿");
        }
        long currentVersion = item.getContentVersion() == null ? 0L : item.getContentVersion();
        if (Constants.CONTENT_MODE_CUSTOM.equals(mode)) {
            String title = dto.getContentTitle() == null ? "" : dto.getContentTitle().trim();
            String body = dto.getContentBody() == null ? "" : dto.getContentBody();
            String prevTitle = item.getContentTitle() == null ? "" : item.getContentTitle();
            String prevBody = item.getContentBody() == null ? "" : item.getContentBody();
            boolean changed = !title.equals(prevTitle) || !body.equals(prevBody);
            if (dto.getContentVersion() != null && dto.getContentVersion() != currentVersion) {
                if (!changed) {
                    return toArticleVo(placement, item, loadOpinions(placement.getId()));
                }
                throw new BusinessException("正文已在别处保存，请刷新后重试（版本冲突）");
            }
            if (!changed && Constants.CONTENT_MODE_CUSTOM.equals(normalizeContentMode(item.getContentMode()))) {
                return toArticleVo(placement, item, loadOpinions(placement.getId()));
            }
            if (!StringUtils.hasText(body)) {
                body = placement.getContentBody() == null ? "" : placement.getContentBody();
                if (!StringUtils.hasText(title)) {
                    title = placement.getContentTitle() == null ? "" : placement.getContentTitle();
                }
            }
            item.setContentTitle(title);
            item.setContentBody(body);
            item.setContentFormat(Constants.CONTENT_FORMAT_LAKE);
            item.setContentVersion(currentVersion + 1);
            item.setContentUpdatedBy(SecurityUtils.getCurrentUsername());
            item.setContentUpdatedAt(LocalDateTime.now());
            if (StringUtils.hasText(title)) {
                item.setTitle(title);
            }
        }
        item.setContentMode(mode);
        itemMapper.updateById(item);
        refreshPlacementContentRollup(placement.getId());
        return toArticleVo(requirePlacement(placement.getId()), requireItem(item.getId()), loadOpinions(placement.getId()));
    }

    @Override
    @Transactional
    public GeoContentArticleVO submitArticleReview(Long itemId) {
        GeoContentPlacementItem item = requireItem(itemId);
        GeoContentPlacement placement = requirePlacement(item.getPlacementId());
        String status = articleStatus(item);
        if (!Constants.CONTENT_STATUS_DRAFTING.equals(status) && !Constants.CONTENT_STATUS_REVISING.equals(status)) {
            throw new BusinessException("只有草稿或修改中的文章可以提交审核");
        }
        if (lakeBlank(effectiveBody(placement, item))) {
            throw new BusinessException("请先写好正文再提交审核");
        }
        item.setContentStatus(Constants.CONTENT_STATUS_PENDING_REVIEW);
        itemMapper.updateById(item);
        refreshPlacementContentRollup(placement.getId());
        return toArticleVo(placement, requireItem(item.getId()), loadOpinions(placement.getId()));
    }

    @Override
    @Transactional
    public GeoContentArticleVO reviewArticle(GeoContentReviewActionDTO dto) {
        if (dto == null || dto.getItemId() == null) {
            throw new BusinessException("平台文章ID不能为空");
        }
        GeoContentPlacementItem item = requireItem(dto.getItemId());
        GeoContentPlacement placement = requirePlacement(item.getPlacementId());
        if (!Constants.CONTENT_STATUS_PENDING_REVIEW.equals(articleStatus(item))) {
            throw new BusinessException("只有待审核的文章可以通过或退回");
        }
        String action = dto.getAction() == null ? "" : dto.getAction().trim();
        String opinion = dto.getOpinion() == null ? "" : dto.getOpinion().trim();
        if ("approve".equals(action)) {
            item.setContentStatus(Constants.CONTENT_STATUS_APPROVED);
            if (StringUtils.hasText(opinion)) {
                insertOpinion(placement.getId(), item.getId(), opinion);
            }
        } else if ("revise".equals(action)) {
            if (!StringUtils.hasText(opinion)) {
                throw new BusinessException("退回修改时请填写意见");
            }
            item.setContentStatus(Constants.CONTENT_STATUS_REVISING);
            insertOpinion(placement.getId(), item.getId(), opinion);
        } else {
            throw new BusinessException("审核动作只能是通过或退回");
        }
        itemMapper.updateById(item);
        refreshPlacementContentRollup(placement.getId());
        return toArticleVo(requirePlacement(placement.getId()), requireItem(item.getId()), loadOpinions(placement.getId()));
    }

    @Override
    @Transactional
    public GeoContentArticleVO addArticleOpinion(GeoContentOpinionSaveDTO dto) {
        if (dto == null || dto.getItemId() == null) {
            throw new BusinessException("平台文章ID不能为空");
        }
        String opinion = dto.getOpinion() == null ? "" : dto.getOpinion().trim();
        if (!StringUtils.hasText(opinion)) {
            throw new BusinessException("请填写意见");
        }
        if (opinion.length() > 2000) {
            throw new BusinessException("意见不能超过 2000 字");
        }
        GeoContentPlacementItem item = requireItem(dto.getItemId());
        insertOpinion(item.getPlacementId(), item.getId(), opinion);
        GeoContentPlacement placement = requirePlacement(item.getPlacementId());
        return toArticleVo(placement, item, loadOpinions(placement.getId()));
    }

    private GeoContentDraftVO toDraftVo(GeoContentPlacement placement) {
        String status = StringUtils.hasText(placement.getContentStatus())
                ? placement.getContentStatus()
                : Constants.CONTENT_STATUS_DRAFTING;
        GeoContentDraftVO vo = new GeoContentDraftVO();
        vo.setPlacementId(placement.getId());
        vo.setTargetQuestion(placement.getTargetQuestion());
        vo.setContentTitle(placement.getContentTitle());
        vo.setContentStatus(status);
        vo.setContentFormat(StringUtils.hasText(placement.getContentFormat())
                ? placement.getContentFormat()
                : Constants.CONTENT_FORMAT_LAKE);
        vo.setContentBody(placement.getContentBody() == null ? "" : placement.getContentBody());
        vo.setContentVersion(placement.getContentVersion() == null ? 0L : placement.getContentVersion());
        boolean editable = !Constants.CONTENT_STATUS_QUEUE.equals(status)
                && !Constants.CONTENT_STATUS_PUBLISHED.equals(status)
                && !Constants.CONTENT_STATUS_PENDING_REVIEW.equals(status);
        vo.setEditable(editable);
        vo.setContentUpdatedBy(placement.getContentUpdatedBy());
        vo.setContentUpdatedAt(placement.getContentUpdatedAt());
        return vo;
    }

    private List<GeoContentPlacementItem> listPlacementItems(Long placementId) {
        return itemMapper.selectList(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .eq(GeoContentPlacementItem::getPlacementId, placementId)
                .orderByAsc(GeoContentPlacementItem::getSortOrder)
                .orderByAsc(GeoContentPlacementItem::getId));
    }

    private GeoContentArticleVO toArticleVo(
            GeoContentPlacement placement,
            GeoContentPlacementItem item,
            Map<Long, List<GeoContentOpinionVO>> opinions) {
        String mode = normalizeContentMode(item.getContentMode());
        boolean custom = Constants.CONTENT_MODE_CUSTOM.equals(mode);
        String status = articleStatus(item);
        GeoContentArticleVO vo = new GeoContentArticleVO();
        vo.setItemId(item.getId());
        vo.setPlacementId(item.getPlacementId());
        vo.setPlatformName(item.getPlatformName());
        vo.setContentForm(normalizeContentForm(item.getContentForm(), item.getPlatformName()));
        vo.setContentMode(mode);
        vo.setContentStatus(status);
        vo.setContentTitle(custom
                ? (item.getContentTitle() == null ? "" : item.getContentTitle())
                : (placement.getContentTitle() == null ? "" : placement.getContentTitle()));
        vo.setContentBody(effectiveBody(placement, item));
        vo.setContentVersion(item.getContentVersion() == null ? 0L : item.getContentVersion());
        vo.setEditable(articleEditable(status));
        vo.setPublishStatus(item.getPublishStatus());
        vo.setOpinions(opinions.getOrDefault(item.getId(), List.of()));
        return vo;
    }

    private Map<Long, List<GeoContentOpinionVO>> loadOpinions(Long placementId) {
        List<GeoContentReviewNote> notes = reviewNoteMapper.selectList(new LambdaQueryWrapper<GeoContentReviewNote>()
                .eq(GeoContentReviewNote::getPlacementId, placementId)
                .orderByAsc(GeoContentReviewNote::getId));
        Map<Long, List<GeoContentOpinionVO>> grouped = new HashMap<>();
        for (GeoContentReviewNote note : notes) {
            GeoContentOpinionVO vo = new GeoContentOpinionVO();
            vo.setId(note.getId());
            vo.setItemId(note.getItemId());
            vo.setOpinion(note.getOpinion());
            vo.setAuthorName(note.getAuthorName());
            vo.setCreateTime(note.getCreateTime());
            grouped.computeIfAbsent(note.getItemId(), key -> new ArrayList<>()).add(vo);
        }
        return grouped;
    }

    private void insertOpinion(Long placementId, Long itemId, String opinion) {
        String text = opinion == null ? "" : opinion.trim();
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (text.length() > 2000) {
            throw new BusinessException("意见不能超过 2000 字");
        }
        GeoContentReviewNote note = new GeoContentReviewNote();
        note.setPlacementId(placementId);
        note.setItemId(itemId);
        note.setOpinion(text);
        note.setAuthorName(currentDisplayName());
        reviewNoteMapper.insert(note);
    }

    /**
     * 统一稿平台行标题：仅在为空，或仍等于旧正文标题时同步为新正文标题。
     * 维护投放已改过的平台标题不覆盖，保证文章列表与维护投放一致。
     */
    private void syncSharedItemTitles(Long placementId, String contentTitle, String previousContentTitle) {
        String title = contentTitle == null ? "" : contentTitle.trim();
        if (!StringUtils.hasText(title)) {
            return;
        }
        String prev = previousContentTitle == null ? "" : previousContentTitle.trim();
        for (GeoContentPlacementItem item : listPlacementItems(placementId)) {
            if (!Constants.CONTENT_MODE_SHARED.equals(normalizeContentMode(item.getContentMode()))) {
                continue;
            }
            String current = item.getTitle() == null ? "" : item.getTitle().trim();
            if (title.equals(current)) {
                continue;
            }
            // 已有手填/发布标题且不等于旧正文标题 → 视为维护投放口径，保留
            if (StringUtils.hasText(current) && !current.equals(prev)) {
                continue;
            }
            item.setTitle(title);
            itemMapper.updateById(item);
        }
    }

    private void markSharedArticlesNeedRevision(Long placementId) {
        for (GeoContentPlacementItem item : listPlacementItems(placementId)) {
            if (!Constants.CONTENT_MODE_SHARED.equals(normalizeContentMode(item.getContentMode()))) {
                continue;
            }
            String status = articleStatus(item);
            if (Constants.CONTENT_STATUS_PENDING_REVIEW.equals(status)
                    || Constants.CONTENT_STATUS_APPROVED.equals(status)
                    || Constants.CONTENT_STATUS_QUEUE.equals(status)) {
                item.setContentStatus(Constants.CONTENT_STATUS_REVISING);
                itemMapper.updateById(item);
            }
        }
    }

    private boolean hasPendingContentReview(Long placementId) {
        Long n = contentReviewMapper.selectCount(new LambdaQueryWrapper<GeoContentReview>()
                .eq(GeoContentReview::getPlacementId, placementId)
                .eq(GeoContentReview::getStatus, "pending"));
        return n != null && n > 0;
    }

    private void refreshPlacementContentRollup(Long placementId) {
        GeoContentPlacement current = placementMapper.selectById(placementId);
        if (current == null) {
            return;
        }
        String cur = current.getContentStatus();
        // 撰写审核链路：待审/一审已过/待投放/已发布 由审核与投放回写，不被平台文章内部状态覆盖
        if (Constants.CONTENT_STATUS_PENDING_REVIEW.equals(cur)
                || Constants.CONTENT_STATUS_STAGE1_PASSED.equals(cur)
                || Constants.CONTENT_STATUS_QUEUE.equals(cur)
                || Constants.CONTENT_STATUS_PUBLISHED.equals(cur)) {
            syncPublishedFromItems(current);
            return;
        }
        List<GeoContentPlacementItem> items = listPlacementItems(placementId);
        String rollup = Constants.CONTENT_STATUS_DRAFTING;
        if (!items.isEmpty()) {
            List<String> statuses = items.stream().map(GeoContentPlacementServiceImpl::articleStatus).toList();
            if (statuses.stream().anyMatch(Constants.CONTENT_STATUS_REVISING::equals)) {
                rollup = Constants.CONTENT_STATUS_REVISING;
            } else if (statuses.stream().anyMatch(Constants.CONTENT_STATUS_DRAFTING::equals)) {
                rollup = Constants.CONTENT_STATUS_DRAFTING;
            } else if (statuses.stream().allMatch(Constants.CONTENT_STATUS_APPROVED::equals)) {
                rollup = Constants.CONTENT_STATUS_APPROVED;
            }
        }
        GeoContentPlacement patch = new GeoContentPlacement();
        patch.setId(placementId);
        patch.setContentStatus(rollup);
        placementMapper.updateById(patch);
    }

    /** 文章列表出现投放成功记录后，主表稿件状态改为已发布 */
    private void syncPublishedFromItems(GeoContentPlacement placement) {
        if (placement == null || placement.getId() == null) {
            return;
        }
        List<GeoContentPlacementItem> items = listPlacementItems(placement.getId());
        boolean hasSuccess = items.stream().anyMatch(i -> Constants.CONTENT_PUBLISH_SUCCESS.equals(i.getPublishStatus()));
        String cur = placement.getContentStatus();
        if (hasSuccess) {
            if (!Constants.CONTENT_STATUS_PUBLISHED.equals(cur)) {
                GeoContentPlacement patch = new GeoContentPlacement();
                patch.setId(placement.getId());
                patch.setContentStatus(Constants.CONTENT_STATUS_PUBLISHED);
                placementMapper.updateById(patch);
            }
            return;
        }
        // 曾标记已发布但投放成功记录没了：回到待投放
        if (Constants.CONTENT_STATUS_PUBLISHED.equals(cur)) {
            GeoContentPlacement patch = new GeoContentPlacement();
            patch.setId(placement.getId());
            patch.setContentStatus(Constants.CONTENT_STATUS_QUEUE);
            placementMapper.updateById(patch);
        }
    }

    private String currentDisplayName() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return SecurityUtils.getCurrentUsername();
        }
        SysUser user = userMapper.selectById(userId);
        return userDisplayName(user);
    }

    private static String articleStatus(GeoContentPlacementItem item) {
        return StringUtils.hasText(item.getContentStatus())
                ? item.getContentStatus()
                : Constants.CONTENT_STATUS_DRAFTING;
    }

    private static boolean articleEditable(String status) {
        return Constants.CONTENT_STATUS_DRAFTING.equals(status)
                || Constants.CONTENT_STATUS_REVISING.equals(status)
                || !StringUtils.hasText(status);
    }

    private static String normalizeContentMode(String mode) {
        return Constants.CONTENT_MODE_CUSTOM.equals(mode)
                ? Constants.CONTENT_MODE_CUSTOM
                : Constants.CONTENT_MODE_SHARED;
    }

    private static String effectiveBody(GeoContentPlacement placement, GeoContentPlacementItem item) {
        if (Constants.CONTENT_MODE_CUSTOM.equals(normalizeContentMode(item.getContentMode()))) {
            return item.getContentBody() == null ? "" : item.getContentBody();
        }
        return placement.getContentBody() == null ? "" : placement.getContentBody();
    }

    private static boolean lakeBlank(String lake) {
        if (!StringUtils.hasText(lake)) {
            return true;
        }
        String text = lake.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").trim();
        return !StringUtils.hasText(text);
    }

    @Override
    public List<SysTaskFileVO> listProofFiles(Long placementId) {
        requirePlacement(placementId);
        String bizType = Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT;
        // 先按文件主键收集，再按 fileUrl 去重：完成时会复制到关联任务，业务侧不应重复展示
        LinkedHashMap<Long, SysTaskFile> byId = new LinkedHashMap<>();
        for (SysTaskFile f : taskFileMapper.selectList(new LambdaQueryWrapper<SysTaskFile>()
                .eq(SysTaskFile::getBizType, bizType)
                .eq(SysTaskFile::getBizId, placementId)
                .orderByAsc(SysTaskFile::getId))) {
            byId.put(f.getId(), f);
        }
        List<SysTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, bizType)
                .eq(SysTask::getBizId, placementId));
        Map<Long, SysTask> taskMap = tasks.stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(SysTask::getId, t -> t, (a, b) -> a));
        if (!taskMap.isEmpty()) {
            for (SysTaskFile f : taskFileMapper.selectList(new LambdaQueryWrapper<SysTaskFile>()
                    .in(SysTaskFile::getTaskId, taskMap.keySet())
                    .orderByAsc(SysTaskFile::getId))) {
                byId.putIfAbsent(f.getId(), f);
                if (!StringUtils.hasText(f.getBizType()) || f.getBizId() == null) {
                    f.setBizType(bizType);
                    f.setBizId(placementId);
                    taskFileMapper.updateById(f);
                }
            }
        }
        LinkedHashMap<String, SysTaskFile> uniqueByUrl = new LinkedHashMap<>();
        for (SysTaskFile f : byId.values()) {
            String key = proofFileDedupeKey(f);
            SysTaskFile existing = uniqueByUrl.get(key);
            if (existing == null || preferProofFile(f, existing)) {
                uniqueByUrl.put(key, f);
            }
        }
        // 按附件上的 taskId 全量回查任务标题/类型（避免只显示 #id）
        Set<Long> fileTaskIds = uniqueByUrl.values().stream()
                .map(SysTaskFile::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SysTask> fileTaskMap = new HashMap<>(taskMap);
        if (!fileTaskIds.isEmpty()) {
            for (SysTask t : taskMapper.selectBatchIds(fileTaskIds)) {
                if (t != null && t.getId() != null) {
                    fileTaskMap.put(t.getId(), t);
                }
            }
        }
        List<SysTaskFileVO> result = new ArrayList<>();
        for (SysTaskFile f : uniqueByUrl.values()) {
            SysTaskFileVO vo = toTaskFileVo(f);
            fillTaskMeta(vo, fileTaskMap.get(f.getTaskId()));
            result.add(vo);
        }
        return result;
    }

    /** 同一物理文件（相同 URL）只算一份；无 URL 时退回主键 */
    private static String proofFileDedupeKey(SysTaskFile f) {
        if (f == null) {
            return "";
        }
        if (StringUtils.hasText(f.getFileUrl())) {
            return f.getFileUrl().trim();
        }
        return "id:" + f.getId();
    }

    /** 业务侧优先展示原始上传，而不是「带入」关联任务的副本 */
    private static boolean preferProofFile(SysTaskFile candidate, SysTaskFile current) {
        boolean candCopy = isCarryCopy(candidate);
        boolean curCopy = isCarryCopy(current);
        if (candCopy != curCopy) {
            return !candCopy;
        }
        Long candId = candidate.getId() == null ? Long.MAX_VALUE : candidate.getId();
        Long curId = current.getId() == null ? Long.MAX_VALUE : current.getId();
        return candId < curId;
    }

    private static boolean isCarryCopy(SysTaskFile f) {
        String remark = f == null ? null : f.getRemark();
        return StringUtils.hasText(remark) && remark.contains("带入");
    }

    private static void fillTaskMeta(SysTaskFileVO vo, SysTask task) {
        if (vo == null || task == null) {
            return;
        }
        vo.setTaskTitle(StringUtils.hasText(task.getTitle()) ? task.getTitle().trim() : null);
        vo.setTaskType(StringUtils.hasText(task.getTaskType()) ? task.getTaskType().trim() : null);
    }

    private SysTaskFileVO toTaskFileVo(SysTaskFile f) {
        SysTaskFileVO vo = new SysTaskFileVO();
        vo.setId(f.getId());
        vo.setTaskId(f.getTaskId());
        vo.setBizType(f.getBizType());
        vo.setBizId(f.getBizId());
        vo.setFileName(f.getFileName());
        vo.setFileUrl(fileStorageService.toPublicUrl(f.getFileUrl()));
        vo.setFileSize(f.getFileSize());
        vo.setContentType(f.getContentType());
        vo.setUploadUserName(f.getUploadUserName());
        vo.setCreateTime(f.getCreateTime());
        return vo;
    }

    @Override
    public List<GeoContentPlacementItemVO> listItems(Long placementId) {
        requirePlacement(placementId);
        List<GeoContentPlacementItem> items = itemMapper.selectList(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .eq(GeoContentPlacementItem::getPlacementId, placementId)
                .orderByAsc(GeoContentPlacementItem::getSortOrder)
                .orderByAsc(GeoContentPlacementItem::getId));

        Map<Long, Long> citeCountByItem = citeMapper.selectList(new LambdaQueryWrapper<GeoContentPlacementCite>()
                        .eq(GeoContentPlacementCite::getPlacementId, placementId)
                        .isNotNull(GeoContentPlacementCite::getItemId)
                        .select(GeoContentPlacementCite::getId, GeoContentPlacementCite::getItemId))
                .stream()
                .collect(Collectors.groupingBy(GeoContentPlacementCite::getItemId, Collectors.counting()));

        List<GeoContentPlacementItemVO> result = new ArrayList<>();
        for (GeoContentPlacementItem item : items) {
            GeoContentPlacementItemVO vo = new GeoContentPlacementItemVO();
            vo.setId(item.getId());
            vo.setPlacementId(item.getPlacementId());
            vo.setTitle(item.getTitle());
            vo.setPlatformName(item.getPlatformName());
            vo.setContentForm(normalizeContentForm(item.getContentForm(), item.getPlatformName()));
            vo.setPublishStatus(item.getPublishStatus());
            vo.setPublishUrl(item.getPublishUrl());
            vo.setPublishTime(item.getPublishTime());
            vo.setRemark(item.getRemark());
            vo.setCiteCount(citeCountByItem.getOrDefault(item.getId(), 0L).intValue());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<GeoContentPlacementCiteVO> listCites(Long placementId, Long itemId) {
        requirePlacement(placementId);
        LambdaQueryWrapper<GeoContentPlacementCite> wrapper = new LambdaQueryWrapper<GeoContentPlacementCite>()
                .eq(GeoContentPlacementCite::getPlacementId, placementId)
                .eq(itemId != null, GeoContentPlacementCite::getItemId, itemId)
                .orderByAsc(GeoContentPlacementCite::getSortOrder)
                .orderByAsc(GeoContentPlacementCite::getId);
        return citeMapper.selectList(wrapper).stream().map(this::toCiteVo).toList();
    }

    @Override
    @Transactional
    public Long create(GeoContentPlacementDTO dto) {
        GeoContentPlacement placement = new GeoContentPlacement();
        fillPlacement(placement, dto);
        assertTopicQuestionUnique(placement.getTopicId(), placement.getTargetQuestion(), null);
        placement.setTitle("");
        placement.setSource(Constants.CONTENT_SOURCE_MANUAL);
        placement.setSourcePlacementId(null);
        placement.setPlacementProgress(Constants.CONTENT_AGG_NONE);
        placement.setContentStatus(Constants.CONTENT_STATUS_DRAFTING);
        placement.setContentFormat(Constants.CONTENT_FORMAT_LAKE);
        placement.setContentBody("");
        placement.setContentVersion(0L);
        placementMapper.insert(placement);
        placementTaskSyncService.ensureTasksForPlacement(placement);
        return placement.getId();
    }

    @Override
    @Transactional
    public void update(GeoContentPlacementDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("内容投放ID不能为空");
        }
        GeoContentPlacement placement = requirePlacement(dto.getId());
        Long previousTopicId = placement.getTopicId();
        String previousQuestion = nz(placement.getTargetQuestion()).trim();
        fillPlacement(placement, dto);
        String nextQuestion = nz(placement.getTargetQuestion()).trim();
        // 仅改发布人/撰写人/备注时不做唯一校验，避免历史重复数据或空白差异把分配拦住
        boolean topicOrQuestionChanged = !Objects.equals(previousTopicId, placement.getTopicId())
                || !previousQuestion.equals(nextQuestion);
        if (topicOrQuestionChanged) {
            assertTopicQuestionUnique(placement.getTopicId(), placement.getTargetQuestion(), dto.getId());
        }
        placementMapper.updateById(placement);
        placementTaskSyncService.ensureTasksForPlacement(placement);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requirePlacement(id);
        citeMapper.delete(new LambdaQueryWrapper<GeoContentPlacementCite>()
                .eq(GeoContentPlacementCite::getPlacementId, id));
        itemMapper.delete(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .eq(GeoContentPlacementItem::getPlacementId, id));
        placementMapper.deleteById(id);
        placementTaskSyncService.deleteTasksForPlacement(id);
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的内容投放");
        }
        for (Long id : ids) {
            if (id != null) {
                delete(id);
            }
        }
    }

    @Override
    public List<GeoSimilarQuestionPreviewVO> generateSimilar(Long placementId, String provider, Integer count) {
        List<GeoSimilarQuestionPreviewVO> preview = previewSimilar(placementId, provider, count);
        if (preview.isEmpty()) {
            throw new BusinessException("未能生成相似问题，请稍后重试");
        }
        return preview;
    }

    @Override
    public List<GeoSimilarQuestionPreviewVO> generateSimilarBatch(List<Long> placementIds, String provider, Integer count) {
        if (placementIds == null || placementIds.isEmpty()) {
            throw new BusinessException("请选择要生成的内容投放");
        }
        int size = normalizeGenerateCount(count);
        List<GeoSimilarQuestionPreviewVO> all = new ArrayList<>();
        int failures = 0;
        for (Long id : placementIds) {
            if (id == null) {
                continue;
            }
            try {
                all.addAll(previewSimilar(id, provider, size));
            } catch (BusinessException e) {
                failures++;
                log.warn("批量生成相似问题跳过 id={}: {}", id, e.getMessage());
            }
        }
        if (all.isEmpty()) {
            throw new BusinessException(failures > 0
                    ? "批量生成没有得到相似问题（可能缺少目标问题或模型调用失败）"
                    : "请选择要生成的内容投放");
        }
        return all;
    }

    @Override
    @Transactional
    public List<GeoContentPlacementListVO> saveSimilarQuestions(GeoSimilarQuestionCommitDTO dto) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("请选择要落库的相似问题");
        }
        List<GeoContentPlacementListVO> created = new ArrayList<>();
        for (GeoSimilarQuestionCommitDTO.Item item : dto.getItems()) {
            if (item == null || item.getSourcePlacementId() == null || !StringUtils.hasText(item.getTargetQuestion())) {
                continue;
            }
            GeoContentPlacement saved = insertSimilarPlacement(item.getSourcePlacementId(), item.getTargetQuestion().trim(), item.getSourceAiModel());
            if (saved != null) {
                created.add(toListVo(saved, List.of(), 0));
            }
        }
        if (created.isEmpty()) {
            throw new BusinessException("所选问题都已存在，未新增");
        }
        return created;
    }

    /** 只生成预览，不写库、不建任务 */
    private List<GeoSimilarQuestionPreviewVO> previewSimilar(Long placementId, String provider, Integer count) {
        GeoContentPlacement source = requirePlacement(placementId);
        if (!StringUtils.hasText(source.getTargetQuestion())) {
            throw new BusinessException("当前记录缺少目标问题，无法生成相似问题");
        }
        int size = normalizeGenerateCount(count);
        SimilarQuestionsResult gen = generateSimilarQuestions(source, provider, size);
        if (gen.questions().isEmpty()) {
            throw new BusinessException("未能生成相似问题，请稍后重试");
        }
        List<GeoSimilarQuestionPreviewVO> rows = new ArrayList<>();
        for (String question : gen.questions()) {
            String trimmed = question == null ? "" : question.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            GeoSimilarQuestionPreviewVO vo = new GeoSimilarQuestionPreviewVO();
            vo.setSourcePlacementId(source.getId());
            vo.setSourceQuestion(source.getTargetQuestion());
            vo.setTargetQuestion(trimmed);
            vo.setSourceAiModel(gen.sourceAiModel());
            vo.setDuplicated(source.getTopicId() != null && existsTopicQuestion(source.getTopicId(), trimmed, null));
            rows.add(vo);
        }
        return rows;
    }

    /** 落库一条相似问题并生成任务；已存在则跳过 */
    private GeoContentPlacement insertSimilarPlacement(Long placementId, String question, String sourceAiModel) {
        GeoContentPlacement source = requirePlacement(placementId);
        if (source.getTopicId() != null && existsTopicQuestion(source.getTopicId(), question, null)) {
            return null;
        }
        GeoContentPlacement row = new GeoContentPlacement();
        applyDefaultPublisherOrUnassigned(row);
        row.setOwnerUserId(null);
        row.setOwnerName(Constants.CONTENT_UNASSIGNED);
        if (source.getTopicId() != null || StringUtils.hasText(source.getTopicName())) {
            applyTopic(row, source.getTopicId(), source.getTopicName());
        } else {
            row.setTopicId(null);
            row.setTopicName("");
        }
        row.setTargetQuestion(question);
        row.setTitle("");
        row.setSource(Constants.CONTENT_SOURCE_AI);
        row.setSourcePlacementId(source.getId());
        row.setSourceAiModel(StringUtils.hasText(sourceAiModel) ? sourceAiModel.trim() : "本地模板");
        row.setPlacementProgress(Constants.CONTENT_AGG_NONE);
        row.setRemark("由「" + source.getTargetQuestion() + "」生成，待分配发布人/撰写人");
        placementMapper.insert(row);
        placementTaskSyncService.ensureTasksForPlacement(row);
        return row;
    }

    private static int normalizeGenerateCount(Integer count) {
        int size = count == null ? 5 : count;
        if (size < 1 || size > 10) {
            throw new BusinessException("生成数量须在 1 到 10 之间");
        }
        return size;
    }

    @Override
    public List<GeoAiProviderOptionVO> listAiProviders() {
        // 仅返回本地模板 + 系统管理中已配 Key 且启用的云厂商
        return sysAiProviderService.listReadyOptions();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GeoContentAiGenerateVO generateArticle(GeoContentAiGenerateDTO dto) {
        return generateArticle(dto, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GeoContentAiGenerateVO generateArticle(GeoContentAiGenerateDTO dto,
                                                  GeoArticlePipelineService.ProgressListener progress) {
        if (dto == null || dto.getPlacementId() == null) {
            throw new BusinessException("内容投放ID不能为空");
        }
        GeoContentPlacement placement = requirePlacement(dto.getPlacementId());
        String lockStatus = placement.getContentStatus();
        if (Constants.CONTENT_STATUS_QUEUE.equals(lockStatus)
                || Constants.CONTENT_STATUS_PUBLISHED.equals(lockStatus)) {
            throw new BusinessException("待投放/已发布文章不可再生成正文");
        }
        if (Constants.CONTENT_STATUS_PENDING_REVIEW.equals(lockStatus)
                && hasPendingContentReview(placement.getId())) {
            throw new BusinessException("审核中不可生成正文");
        }

        String provider = StringUtils.hasText(dto.getProvider()) ? dto.getProvider().trim() : null;
        if (provider != null && "local".equalsIgnoreCase(provider)) {
            throw new BusinessException("请选择已配置 API Key 的云厂商模型生成文章");
        }
        if (provider != null && !aiChatService.isProviderConfigured(provider)) {
            throw new BusinessException("所选模型未配置 API Key，请到「系统管理 → AI模型配置」中填写");
        }
        if (provider == null && !aiChatService.isEnabled()) {
            throw new BusinessException("未配置可用 AI 模型，请到「系统管理 → AI模型配置」中填写");
        }

        // 后台两阶段流水线（大纲→扩写→插图→质检重写）；前端异步轮询进度
        GeoArticlePipelineResultVO pipeline;
        try {
            pipeline = articlePipelineService.run(placement, dto, provider, progress);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 生成失败: " + e.getMessage());
        }

        if (progress != null) {
            progress.onStage("save", "写入草稿", "running", "正在写入统一稿/独立稿…");
        }

        String title = pipeline.getTitle();
        String lake = toLakeDocument(pipeline.getHtml());
        String modelLabel = pipeline.getSourceAiModel();

        Long itemId = dto.getItemId();
        Long version;
        if (itemId != null) {
            GeoContentPlacementItem item = itemMapper.selectById(itemId);
            if (item == null || !Objects.equals(item.getPlacementId(), placement.getId())) {
                throw new BusinessException("平台文章不存在");
            }
            GeoContentItemDraftSaveDTO save = new GeoContentItemDraftSaveDTO();
            save.setItemId(itemId);
            save.setContentMode(Constants.CONTENT_MODE_CUSTOM);
            save.setContentTitle(title);
            save.setContentBody(lake);
            save.setContentVersion(item.getContentVersion());
            GeoContentArticleVO saved = saveItemDraft(save);
            version = saved.getContentVersion();
        } else {
            GeoContentDraftSaveDTO save = new GeoContentDraftSaveDTO();
            save.setPlacementId(placement.getId());
            save.setContentTitle(title);
            save.setContentBody(lake);
            save.setContentVersion(placement.getContentVersion());
            GeoContentDraftVO saved = saveDraft(save);
            version = saved.getContentVersion();
        }

        placementMapper.update(null, new LambdaUpdateWrapper<GeoContentPlacement>()
                .eq(GeoContentPlacement::getId, placement.getId())
                .set(GeoContentPlacement::getSource, Constants.CONTENT_SOURCE_AI)
                .set(GeoContentPlacement::getSourceAiModel, modelLabel));

        GeoContentAiGenerateVO vo = new GeoContentAiGenerateVO();
        vo.setContentTitle(title);
        vo.setContentBody(lake);
        vo.setSourceAiModel(modelLabel);
        vo.setUsedPointCount(pipeline.getUsedPointCount());
        vo.setUsedImageCount(pipeline.getUsedImageCount());
        vo.setQaRounds(pipeline.getQaRounds());
        vo.setStyleCode(pipeline.getStyleCode());
        vo.setSkeletonCode(pipeline.getSkeletonCode());
        vo.setStages(pipeline.getStages());
        vo.setApplied(true);
        vo.setContentVersion(version);
        return vo;
    }

    private ParsedArticle parseGeneratedArticle(String content) {
        String title = "";
        String html = "";
        try {
            String text = content == null ? "" : content.trim();
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
            JsonNode node = objectMapper.readTree(text);
            if (node.has("title")) {
                title = node.path("title").asText("").trim();
            }
            if (node.has("html")) {
                html = node.path("html").asText("").trim();
            } else if (node.has("body")) {
                html = node.path("body").asText("").trim();
            } else if (node.has("content")) {
                html = node.path("content").asText("").trim();
            }
        } catch (Exception e) {
            log.warn("解析 AI 文章 JSON 失败，按纯文本兜底: {}", e.getMessage());
            html = content == null ? "" : content.trim();
        }
        if (!StringUtils.hasText(html)) {
            throw new BusinessException("AI 未返回可用正文，请重试");
        }
        if (!StringUtils.hasText(title)) {
            title = firstLineAsTitle(html);
        }
        if (!html.contains("<")) {
            html = plainToHtmlParagraphs(html);
        }
        html = normalizePublishableHtml(html);
        return new ParsedArticle(title, html);
    }

    private static String toLakeDocument(String html) {
        String body = html == null ? "" : html.trim();
        if (body.regionMatches(true, 0, "<!doctype lake>", 0, "<!doctype lake>".length())) {
            return body;
        }
        return "<!doctype lake><meta name=\"doc-version\" content=\"1\" />" + body;
    }

    /**
     * 把 AI 正文整理成内容平台可直接粘贴的语义 HTML：清掉 markdown 残留、危险标签，
     * 并把孤立换行尽量落成段落。
     */
    private static String normalizePublishableHtml(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String html = raw.trim();
        // 去掉偶发的 markdown 标题/加粗写法
        html = html.replaceAll("(?m)^#{1,6}\\s*", "");
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("(?m)^[-*•]\\s+", "• ");
        // 降级不允许的标题级别
        html = html.replaceAll("(?i)</?h1\\b[^>]*>", "");
        html = html.replaceAll("(?i)<h([4-6])\\b[^>]*>", "<h3>");
        html = html.replaceAll("(?i)</h([4-6])>", "</h3>");
        // 去掉脚本/样式/链接与危险属性
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        html = html.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        html = html.replaceAll("(?i)</?a\\b[^>]*>", "");
        html = html.replaceAll("(?i)\\s(style|class|onclick|onerror)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "");
        // 若几乎没有块级标签，按空行拆段
        if (!html.matches("(?is).*<\\s*(p|h2|h3|ul|ol|blockquote)\\b.*")) {
            html = plainToHtmlParagraphs(html);
        }
        return html.trim();
    }

    private static String plainToHtmlParagraphs(String text) {
        String[] parts = text.replace("\r\n", "\n").split("\n{2,}");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String line = part.trim();
            if (line.isEmpty()) {
                continue;
            }
            // 纯文本里的「一、xxx / 1. xxx」升成小标题，便于平台粘贴
            if (line.matches("^[一二三四五六七八九十]+[、.．]\\s*.+")
                    || line.matches("^\\d+[、.．]\\s*.+")
                    || line.matches("^【.+】$")) {
                String heading = line.replaceFirst("^[一二三四五六七八九十\\d]+[、.．]\\s*", "")
                        .replaceAll("^【|】$", "");
                sb.append("<h2>").append(heading).append("</h2>");
                continue;
            }
            if (line.startsWith("• ") || line.startsWith("- ") || line.startsWith("* ")) {
                sb.append("<ul><li>").append(line.substring(2).trim()).append("</li></ul>");
                continue;
            }
            sb.append("<p>").append(line.replace("\n", "<br/>")).append("</p>");
        }
        return sb.isEmpty() ? "<p>" + text.trim() + "</p>" : sb.toString();
    }

    private static String firstLineAsTitle(String html) {
        String plain = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (plain.length() > 40) {
            return plain.substring(0, 40);
        }
        return StringUtils.hasText(plain) ? plain : "未命名文章";
    }

    private record ParsedArticle(String title, String html) {
    }

    private record SimilarQuestionsResult(List<String> questions, String sourceAiModel) {
    }

    private SimilarQuestionsResult generateSimilarQuestions(GeoContentPlacement source, String provider, int count) {
        String topic = nz(source.getTopicName());
        String question = source.getTargetQuestion().trim();
        String p = StringUtils.hasText(provider) ? provider : null;
        // 显式选本地，或未选且默认也是本地
        boolean forceLocal = p != null && "local".equalsIgnoreCase(p.trim());
        boolean useAi = !forceLocal && (p != null ? aiChatService.isProviderConfigured(p) : aiChatService.isEnabled());
        if (useAi) {
            try {
                String system = """
                        你是 GEO 内容投放助手。请围绕给定目标问题，生成恰好 %d 条语义相近但表述不同的「待投放目标问题」。
                        要求：
                        1. 只输出 JSON 数组，例如 ["问题1","问题2"]，不要 markdown，不要解释；
                        2. 问题要适合中文内容投放，口语化、可检索；
                        3. 不要与原问题完全相同，也不要重复；
                        4. 数组长度必须是 %d。
                        """.formatted(count, count);
                String user = "话题：" + (topic.isEmpty() ? "未指定" : topic) + "\n原目标问题：" + question;
                String content = aiChatService.chat(system, user, p);
                List<String> parsed = parseQuestionArray(content);
                if (!parsed.isEmpty()) {
                    List<String> questions = parsed.stream().filter(q -> !q.equals(question)).distinct().limit(count).toList();
                    return new SimilarQuestionsResult(questions, resolveAiSourceLabel(p));
                }
            } catch (BusinessException e) {
                if (p != null && !"local".equalsIgnoreCase(p.trim())) {
                    throw e;
                }
                log.warn("AI 生成相似问题失败，改用本地启发式: {}", e.getMessage());
            } catch (Exception e) {
                if (p != null && !"local".equalsIgnoreCase(p.trim())) {
                    throw new BusinessException("AI 生成失败: " + e.getMessage());
                }
                log.warn("AI 生成相似问题失败，改用本地启发式: {}", e.getMessage());
            }
        } else if (p != null && !"local".equalsIgnoreCase(p.trim()) && !aiChatService.isProviderConfigured(p)) {
            throw new BusinessException("所选模型未配置 API Key，请到「系统管理 → AI模型配置」中填写");
        }
        return new SimilarQuestionsResult(heuristicSimilarQuestions(topic, question, count), "本地模板");
    }

    /** 生成来源展示：厂商名（模型名） */
    private String resolveAiSourceLabel(String provider) {
        if (!StringUtils.hasText(provider) || "local".equalsIgnoreCase(provider.trim())) {
            return "本地模板";
        }
        return sysAiProviderService.findReady(provider)
                .map(row -> {
                    String name = StringUtils.hasText(row.getProviderName()) ? row.getProviderName() : row.getProvider();
                    if (StringUtils.hasText(row.getModel())) {
                        return name + "（" + row.getModel().trim() + "）";
                    }
                    return name;
                })
                .orElseGet(() -> provider.trim());
    }

    private List<String> parseQuestionArray(String content) {
        try {
            String text = content.trim();
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
            JsonNode arr = objectMapper.readTree(text);
            List<String> list = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    String q = node.asText("").trim();
                    if (StringUtils.hasText(q)) {
                        list.add(q);
                    }
                }
            }
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> heuristicSimilarQuestions(String topic, String question, int count) {
        String base = question.replaceAll("[？?！!。]$", "").trim();
        String topicPrefix = StringUtils.hasText(topic) ? topic : "相关场景";
        List<String> pool = new ArrayList<>(GeoSimilarQuestionTemplates.ALL);
        Collections.shuffle(pool, ThreadLocalRandom.current());
        List<String> picked = new ArrayList<>();
        for (String tpl : pool) {
            String q = tpl.replace("{base}", base).replace("{topic}", topicPrefix).trim();
            if (!StringUtils.hasText(q) || q.equals(question) || picked.contains(q)) {
                continue;
            }
            picked.add(q);
            if (picked.size() >= count) {
                break;
            }
        }
        return picked;
    }

    @Override
    @Transactional
    public Long createItem(GeoContentPlacementItemDTO dto) {
        requirePlacement(dto.getPlacementId());
        GeoContentPlacementItem item = new GeoContentPlacementItem();
        fillItem(item, dto);
        if (item.getSortOrder() == null) {
            item.setSortOrder(nextItemSort(dto.getPlacementId()));
        }
        itemMapper.insert(item);
        syncPlacementDerived(dto.getPlacementId());
        placementTaskSyncService.completeArticlePublishTasks(dto.getPlacementId());
        return item.getId();
    }

    @Override
    @Transactional
    public void ensureQueueArticleItems(Long placementId) {
        GeoContentPlacement patch = new GeoContentPlacement();
        patch.setId(placementId);
        patch.setContentStatus(Constants.CONTENT_STATUS_QUEUE);
        patch.setPlacementProgress(Constants.CONTENT_AGG_PENDING);
        placementMapper.updateById(patch);
    }

    @Override
    @Transactional
    public void updateItem(GeoContentPlacementItemDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("发布详情ID不能为空");
        }
        if (dto.getPlacementId() == null) {
            throw new BusinessException("请选择目标问题");
        }
        GeoContentPlacementItem item = requireItem(dto.getId());
        Long oldPlacementId = item.getPlacementId();
        Long newPlacementId = dto.getPlacementId();
        // 允许已发布文章改绑话题/目标问题（换投放单）；引用记录同步迁到新投放单
        requirePlacement(newPlacementId);
        String oldUrl = nz(item.getPublishUrl());
        fillItem(item, dto);
        itemMapper.updateById(item);
        if (!Objects.equals(oldPlacementId, newPlacementId)) {
            GeoContentPlacementCite move = new GeoContentPlacementCite();
            move.setPlacementId(newPlacementId);
            citeMapper.update(move, new LambdaQueryWrapper<GeoContentPlacementCite>()
                    .eq(GeoContentPlacementCite::getItemId, item.getId()));
        }
        String newUrl = nz(item.getPublishUrl());
        if (!Objects.equals(oldUrl, newUrl)) {
            GeoContentPlacementCite patch = new GeoContentPlacementCite();
            patch.setCiteUrl(newUrl);
            citeMapper.update(patch, new LambdaQueryWrapper<GeoContentPlacementCite>()
                    .eq(GeoContentPlacementCite::getItemId, item.getId()));
        }
        syncPlacementDerived(oldPlacementId);
        if (!Objects.equals(oldPlacementId, newPlacementId)) {
            syncPlacementDerived(newPlacementId);
        }
        placementTaskSyncService.completeArticlePublishTasks(newPlacementId);
    }

    @Override
    @Transactional
    public void deleteItem(Long itemId) {
        GeoContentPlacementItem item = requireItem(itemId);
        Long placementId = item.getPlacementId();
        citeMapper.delete(new LambdaQueryWrapper<GeoContentPlacementCite>()
                .eq(GeoContentPlacementCite::getItemId, itemId));
        itemMapper.deleteById(itemId);
        syncPlacementDerived(placementId);
    }

    @Override
    @Transactional
    public void deleteItems(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException("请选择要删除的文章");
        }
        for (Long itemId : itemIds) {
            if (itemId != null) {
                deleteItem(itemId);
            }
        }
    }

    @Override
    @Transactional
    public Long createCite(GeoContentPlacementCiteDTO dto) {
        requirePlacement(dto.getPlacementId());
        if (dto.getItemId() != null) {
            GeoContentPlacementItem item = requireItem(dto.getItemId());
            if (!item.getPlacementId().equals(dto.getPlacementId())) {
                throw new BusinessException("引用与发布详情不匹配");
            }
        }
        assertCiteUnique(dto.getPlacementId(), dto.getItemId(), dto.getAskQuestion(), dto.getAiPlatform(), null);
        GeoContentPlacementCite cite = new GeoContentPlacementCite();
        fillCite(cite, dto);
        if (cite.getSortOrder() == null) {
            cite.setSortOrder(nextCiteSort(dto.getPlacementId()));
        }
        citeMapper.insert(cite);
        return cite.getId();
    }

    @Override
    @Transactional
    public void updateCite(GeoContentPlacementCiteDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("引用ID不能为空");
        }
        GeoContentPlacementCite cite = requireCite(dto.getId());
        if (!cite.getPlacementId().equals(dto.getPlacementId())) {
            throw new BusinessException("引用与内容投放不匹配");
        }
        if (dto.getItemId() != null) {
            GeoContentPlacementItem item = requireItem(dto.getItemId());
            if (!item.getPlacementId().equals(dto.getPlacementId())) {
                throw new BusinessException("引用与发布详情不匹配");
            }
        }
        Long itemId = dto.getItemId() != null ? dto.getItemId() : cite.getItemId();
        assertCiteUnique(dto.getPlacementId(), itemId, dto.getAskQuestion(), dto.getAiPlatform(), dto.getId());
        fillCite(cite, dto);
        citeMapper.updateById(cite);
    }

    @Override
    @Transactional
    public void deleteCite(Long citeId) {
        requireCite(citeId);
        citeMapper.deleteById(citeId);
    }

    @Override
    @Transactional
    public GeoImportResultVO importExcel(InputStream in) {
        if (!importLock.tryLock()) {
            throw new BusinessException("已有内容投放导入正在进行，请等待完成后再试，不要重复点击");
        }
        try {
            return doImportExcel(in);
        } finally {
            importLock.unlock();
        }
    }

    private GeoImportResultVO doImportExcel(InputStream in) {
        GeoImportResultVO result = new GeoImportResultVO();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<CiteHeader> citeHeaders = readCiteHeaders(sheet);
            int remarkCol = citeHeaders.get(citeHeaders.size() - 1).col() + 1;
            Long currentPlacementId = null;
            Long lastItemId = null;
            int itemSort = 0;
            int citeSort = 0;
            Set<String> dedupeCites = new HashSet<>();
            Map<String, Long> placementKeyCache = new LinkedHashMap<>();
            int totalRows = Math.max(sheet.getLastRowNum() - 1, 1);
            int doneRows = 0;

            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                doneRows++;
                GeoImportProgress.report(doneRows, totalRows);
                // 序号必须用原始单元格，合并展开会把续行误判成新内容组
                String seq = ExcelCellUtils.rawStr(sheet, r, 0);
                String publisher = ExcelCellUtils.str(sheet, r, 1);
                String owner = ExcelCellUtils.str(sheet, r, 2);
                String topic = ExcelCellUtils.str(sheet, r, 3);
                String targetQuestion = ExcelCellUtils.str(sheet, r, 4);
                String title = ExcelCellUtils.str(sheet, r, 5);
                // 平台/链接/时间用原始单元格，避免合并单元格把引用续行误判为新投放
                String linkOrStatus = ExcelCellUtils.rawStr(sheet, r, 6);
                String platform = ExcelCellUtils.rawStr(sheet, r, 7);
                String publishTimeRaw = ExcelCellUtils.rawStr(sheet, r, 8);
                String askQuestion = ExcelCellUtils.rawStr(sheet, r, 9);
                boolean hasCiteUrl = false;
                for (CiteHeader header : citeHeaders) {
                    if (StringUtils.hasText(ExcelCellUtils.rawStr(sheet, r, header.col()))) {
                        hasCiteUrl = true;
                        break;
                    }
                }
                String remark = ExcelCellUtils.str(sheet, r, remarkCol);

                boolean newGroup = StringUtils.hasText(seq);
                boolean hasPlatform = StringUtils.hasText(platform);
                boolean hasAsk = StringUtils.hasText(askQuestion);

                if (isTemplateSample(seq, targetQuestion, title, askQuestion, remark, linkOrStatus)) {
                    if (newGroup) {
                        currentPlacementId = null;
                        lastItemId = null;
                    }
                    continue;
                }

                if (!newGroup && !hasPlatform && !hasAsk && !hasCiteUrl) {
                    continue;
                }

                if (newGroup) {
                    if (!StringUtils.hasText(title) && !StringUtils.hasText(targetQuestion)) {
                        // 空壳序号行（模板预留），静默跳过
                        currentPlacementId = null;
                        lastItemId = null;
                        continue;
                    }
                    try {
                        String key = placementKey(publisher, owner, topic, targetQuestion, title);
                        Long existingId = placementKeyCache.get(key);
                        if (existingId == null) {
                            GeoContentPlacement existing = findByBizKey(publisher, owner, topic, targetQuestion, title);
                            if (existing != null) {
                                existingId = existing.getId();
                                clearChildren(existingId);
                                existing.setRemark(remark);
                                existing.setSource(Constants.CONTENT_SOURCE_IMPORT);
                                existing.setPlacementProgress(Constants.CONTENT_AGG_NONE);
                                applyUsersAndTopic(existing, publisher, owner, topic);
                                placementMapper.updateById(existing);
                                placementTaskSyncService.ensureTasksForPlacement(existing);
                                result.setUpdateCount(result.getUpdateCount() + 1);
                            } else {
                                GeoContentPlacement placement = new GeoContentPlacement();
                                placement.setTargetQuestion(nz(targetQuestion));
                                placement.setTitle(nz(title));
                                placement.setSource(Constants.CONTENT_SOURCE_IMPORT);
                                placement.setPlacementProgress(Constants.CONTENT_AGG_NONE);
                                placement.setRemark(remark);
                                applyUsersAndTopic(placement, publisher, owner, topic);
                                placementMapper.insert(placement);
                                placementTaskSyncService.ensureTasksForPlacement(placement);
                                existingId = placement.getId();
                                result.setInsertCount(result.getInsertCount() + 1);
                            }
                            placementKeyCache.put(key, existingId);
                        }
                        currentPlacementId = existingId;
                        lastItemId = null;
                        itemSort = 0;
                        citeSort = 0;
                        dedupeCites = new HashSet<>();
                        result.setTotalCount(result.getTotalCount() + 1);
                    } catch (BusinessException e) {
                        currentPlacementId = null;
                        lastItemId = null;
                        result.setTotalCount(result.getTotalCount() + 1);
                        result.setFailureCount(result.getFailureCount() + 1);
                        result.getErrors().add(new GeoImportResultVO.GeoImportErrorVO(r + 1, "话题", e.getMessage()));
                        continue;
                    }
                }

                if (currentPlacementId == null) {
                    continue;
                }

                if (hasPlatform) {
                    GeoContentPlacementItem item = new GeoContentPlacementItem();
                    item.setPlacementId(currentPlacementId);
                    item.setTitle(nz(title));
                    item.setPlatformName(platform.trim());
                    item.setContentForm(inferContentForm(platform));
                    ParsedPublish parsed = parsePublish(linkOrStatus);
                    item.setPublishStatus(parsed.status());
                    item.setPublishUrl(parsed.url());
                    item.setPublishTime(atStartOfDay(parseFlexibleDate(publishTimeRaw)));
                    item.setSortOrder(itemSort++);
                    item.setRemark("");
                    itemMapper.insert(item);
                    lastItemId = item.getId();
                }

                if (hasAsk && hasCiteUrl) {
                    for (CiteHeader header : citeHeaders) {
                        String url = ExcelCellUtils.rawStr(sheet, r, header.col());
                        if (!StringUtils.hasText(url)) {
                            continue;
                        }
                        // 同一发布明细下：提问问题 + AI 平台唯一（忽略链接差异）
                        String citeKey = String.valueOf(lastItemId) + "\0" + askQuestion.trim() + "\0" + header.platform();
                        if (!dedupeCites.add(citeKey)) {
                            continue;
                        }
                        Long existed = citeMapper.selectCount(new LambdaQueryWrapper<GeoContentPlacementCite>()
                                .eq(GeoContentPlacementCite::getItemId, lastItemId)
                                .eq(GeoContentPlacementCite::getAskQuestion, askQuestion.trim())
                                .eq(GeoContentPlacementCite::getAiPlatform, header.platform()));
                        if (existed != null && existed > 0) {
                            continue;
                        }
                        GeoContentPlacementCite cite = new GeoContentPlacementCite();
                        cite.setPlacementId(currentPlacementId);
                        cite.setItemId(lastItemId);
                        cite.setAskQuestion(askQuestion.trim());
                        cite.setAiPlatform(header.platform());
                        cite.setCiteUrl(url.trim());
                        cite.setSortOrder(citeSort++);
                        citeMapper.insert(cite);
                    }
                }
            }
            // 导入结束后按发布详情回写主表投放进度与标题
            for (Long placementId : placementKeyCache.values()) {
                syncPlacementDerived(placementId);
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("Lock wait timeout")) {
                throw new BusinessException("导入被其他操作占用，请稍后再试，不要重复点击");
            }
            throw new BusinessException("导入内容投放失败: " + msg);
        }
        return result;
    }

    @Override
    @Transactional
    public String seedIfEmpty() {
        Long count = placementMapper.selectCount(null);
        if (count != null && count > 0) {
            // 开发期允许通过 -Dgeo.content.reseed=true 强制按 Excel 重刷
            if (!Boolean.parseBoolean(System.getProperty("geo.content.reseed", "false"))) {
                return "已有 " + count + " 条，跳过";
            }
            log.warn("强制重刷内容投放样例，清空现有 {} 条", count);
            List<GeoContentPlacement> all = placementMapper.selectList(null);
            for (GeoContentPlacement row : all) {
                clearChildren(row.getId());
                placementMapper.deleteById(row.getId());
            }
        }
        try (InputStream in = openSeed("db/seed/geo-content-placement.xlsx", "内容投放.xlsx")) {
            GeoImportResultVO result = importExcel(in);
            return "新增 %d / 更新 %d / 失败 %d".formatted(
                    result.getInsertCount(), result.getUpdateCount(), result.getFailureCount());
        } catch (Exception e) {
            throw new RuntimeException("刷入内容投放样例失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public int backfillTopicRelations() {
        List<GeoContentPlacement> rows = placementMapper.selectList(new LambdaQueryWrapper<GeoContentPlacement>()
                .isNull(GeoContentPlacement::getTopicId)
                .isNotNull(GeoContentPlacement::getTopicName)
                .ne(GeoContentPlacement::getTopicName, ""));
        int updated = 0;
        for (GeoContentPlacement row : rows) {
            GeoTopic topic = topicService.getOrCreate(row.getTopicName(), null);
            row.setTopicId(topic.getId());
            row.setTopicName(topic.getTopicName());
            placementMapper.updateById(row);
            updated++;
        }
        return updated;
    }

    @Override
    @Transactional
    public int backfillPlacementProgress() {
        List<GeoContentPlacement> rows = placementMapper.selectList(null);
        for (GeoContentPlacement row : rows) {
            syncPlacementDerived(row.getId());
        }
        return rows.size();
    }

    private GeoContentPlacement requirePlacement(Long id) {
        GeoContentPlacement entity = placementMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("内容投放不存在");
        }
        return entity;
    }

    private GeoContentPlacementItem requireItem(Long id) {
        GeoContentPlacementItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("发布详情不存在");
        }
        return item;
    }

    private GeoContentPlacementCite requireCite(Long id) {
        GeoContentPlacementCite cite = citeMapper.selectById(id);
        if (cite == null) {
            throw new BusinessException("引用记录不存在");
        }
        return cite;
    }

    private void fillPlacement(GeoContentPlacement placement, GeoContentPlacementDTO dto) {
        applyUser(placement, true, dto.getPublisherUserId());
        applyUser(placement, false, dto.getOwnerUserId());
        applyTopic(placement, dto.getTopicId(), dto.getTopicName());
        placement.setTargetQuestion(nz(dto.getTargetQuestion()).trim());
        placement.setRemark(dto.getRemark());
    }

    private void assertTopicQuestionUnique(Long topicId, String targetQuestion, Long excludeId) {
        String question = targetQuestion == null ? "" : targetQuestion.trim();
        if (topicId == null || !StringUtils.hasText(question)) {
            return;
        }
        if (existsTopicQuestion(topicId, question, excludeId)) {
            throw new BusinessException("同一话题下目标问题已存在");
        }
    }

    private boolean existsTopicQuestion(Long topicId, String targetQuestion, Long excludeId) {
        String question = targetQuestion == null ? "" : targetQuestion.trim();
        if (topicId == null || !StringUtils.hasText(question)) {
            return false;
        }
        LambdaQueryWrapper<GeoContentPlacement> wrapper = new LambdaQueryWrapper<GeoContentPlacement>()
                .eq(GeoContentPlacement::getTopicId, topicId)
                .apply("TRIM(target_question) = {0}", question);
        if (excludeId != null) {
            wrapper.ne(GeoContentPlacement::getId, excludeId);
        }
        return placementMapper.selectCount(wrapper) > 0;
    }

    private void applyUsersAndTopic(GeoContentPlacement placement, String publisher, String owner, String topicName) {
        SysUser publisherUser = findUserByName(publisher);
        if (publisherUser != null) {
            placement.setPublisherUserId(publisherUser.getUserId());
            placement.setPublisherName(userDisplayName(publisherUser));
        } else if (StringUtils.hasText(publisher) && !Constants.CONTENT_UNASSIGNED.equals(publisher.trim())) {
            placement.setPublisherUserId(null);
            placement.setPublisherName(publisher.trim());
        } else {
            applyDefaultPublisherOrUnassigned(placement);
        }

        SysUser ownerUser = findUserByName(owner);
        if (ownerUser != null) {
            placement.setOwnerUserId(ownerUser.getUserId());
            placement.setOwnerName(userDisplayName(ownerUser));
        } else if (StringUtils.hasText(owner) && !Constants.CONTENT_UNASSIGNED.equals(owner.trim())) {
            placement.setOwnerUserId(null);
            placement.setOwnerName(owner.trim());
        } else {
            placement.setOwnerUserId(null);
            placement.setOwnerName(Constants.CONTENT_UNASSIGNED);
        }

        applyTopic(placement, null, topicName);
    }

    private void applyUser(GeoContentPlacement placement, boolean publisher, Long userId) {
        if (userId == null) {
            if (publisher) {
                applyDefaultPublisherOrUnassigned(placement);
            } else {
                placement.setOwnerUserId(null);
                placement.setOwnerName(Constants.CONTENT_UNASSIGNED);
            }
            return;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Objects.equals(user.getStatus(), Constants.STATUS_ACTIVE)) {
            throw new BusinessException((publisher ? "发布人" : "撰写人") + "用户不存在或已停用");
        }
        if (publisher) {
            placement.setPublisherUserId(user.getUserId());
            placement.setPublisherName(userDisplayName(user));
        } else {
            placement.setOwnerUserId(user.getUserId());
            placement.setOwnerName(userDisplayName(user));
        }
    }

    /** 未指定发布人时套用「发布人规则」中的默认发布人，否则记为待分配 */
    private void applyDefaultPublisherOrUnassigned(GeoContentPlacement placement) {
        var def = publisherRuleService.resolveDefault();
        if (def.isPresent() && def.get().getPublisherUserId() != null) {
            placement.setPublisherUserId(def.get().getPublisherUserId());
            placement.setPublisherName(StringUtils.hasText(def.get().getPublisherName())
                    ? def.get().getPublisherName().trim()
                    : ("用户" + def.get().getPublisherUserId()));
            return;
        }
        placement.setPublisherUserId(null);
        placement.setPublisherName(Constants.CONTENT_UNASSIGNED);
    }

    private void applyTopic(GeoContentPlacement placement, Long topicId, String topicName) {
        GeoTopic topic = null;
        if (topicId != null) {
            topic = topicService.getById(topicId);
        } else if (StringUtils.hasText(topicName)) {
            // 名称已存在则只关联；不存在则建档后再关联
            topic = topicService.getOrCreate(topicName.trim(), null);
        }
        if (topic == null) {
            throw new BusinessException("请选择话题");
        }
        placement.setTopicId(topic.getId());
        placement.setTopicName(topic.getTopicName());
    }

    private SysUser findUserByName(String name) {
        String key = nz(name);
        if (!StringUtils.hasText(key) || Constants.CONTENT_UNASSIGNED.equals(key)) {
            return null;
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, Constants.STATUS_ACTIVE)
                .and(w -> w.eq(SysUser::getNickname, key).or().eq(SysUser::getUsername, key))
                .last("LIMIT 1"));
        return users.isEmpty() ? null : users.getFirst();
    }

    private static String userDisplayName(SysUser user) {
        if (user == null) {
            return Constants.CONTENT_UNASSIGNED;
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername().trim() : Constants.CONTENT_UNASSIGNED;
    }

    private void fillItem(GeoContentPlacementItem item, GeoContentPlacementItemDTO dto) {
        item.setPlacementId(dto.getPlacementId());
        item.setTitle(nz(dto.getTitle()));
        item.setPlatformName(nz(dto.getPlatformName()));
        item.setContentForm(normalizeContentForm(dto.getContentForm(), dto.getPlatformName()));
        String publishStatus = normalizePublishStatus(dto.getPublishStatus());
        item.setPublishStatus(publishStatus);
        // 待投放/未投放允许先补平台，链接与时间可稍后填写；投放成功时必须有合法链接
        if (Constants.CONTENT_PUBLISH_SUCCESS.equals(publishStatus)
                || StringUtils.hasText(dto.getPublishUrl())) {
            assertPublishUrl(dto.getPublishUrl());
        }
        item.setPublishUrl(nz(dto.getPublishUrl()));
        if (Constants.CONTENT_PUBLISH_SUCCESS.equals(publishStatus) && dto.getPublishTime() == null) {
            throw new BusinessException("投放成功时请填写发布时间");
        }
        item.setPublishTime(dto.getPublishTime());
        item.setSortOrder(dto.getSortOrder());
        item.setRemark(dto.getRemark());
    }

    private void fillCite(GeoContentPlacementCite cite, GeoContentPlacementCiteDTO dto) {
        if (!StringUtils.hasText(dto.getAskQuestion())) {
            throw new BusinessException("请填写提问问题");
        }
        if (!StringUtils.hasText(dto.getAiPlatform())) {
            throw new BusinessException("请选择AI平台");
        }
        if (!StringUtils.hasText(dto.getCiteUrl()) && !StringUtils.hasText(dto.getScreenshotUrl())) {
            throw new BusinessException("请填写引用链接或上传截图");
        }
        cite.setPlacementId(dto.getPlacementId());
        cite.setItemId(dto.getItemId());
        cite.setAskQuestion(nz(dto.getAskQuestion()));
        cite.setAiPlatform(nz(dto.getAiPlatform()));
        cite.setCiteUrl(nz(dto.getCiteUrl()));
        String shot = fileStorageService.normalizeStoragePath(dto.getScreenshotUrl());
        cite.setScreenshotUrl(shot);
        cite.setSortOrder(dto.getSortOrder());
        cite.setRemark(dto.getRemark());
    }

    /**
     * 同一发布明细（文章）下，提问问题 + AI 平台唯一；未挂明细时退化为同一投放主单下唯一。
     */
    private void assertCiteUnique(Long placementId, Long itemId, String askQuestion, String aiPlatform, Long excludeId) {
        String question = nz(askQuestion);
        String platform = nz(aiPlatform);
        if (!StringUtils.hasText(question) || !StringUtils.hasText(platform)) {
            throw new BusinessException("提问问题和AI平台不能为空");
        }
        LambdaQueryWrapper<GeoContentPlacementCite> wrapper = new LambdaQueryWrapper<GeoContentPlacementCite>()
                .eq(GeoContentPlacementCite::getAskQuestion, question)
                .eq(GeoContentPlacementCite::getAiPlatform, platform);
        if (itemId != null) {
            wrapper.eq(GeoContentPlacementCite::getItemId, itemId);
        } else {
            wrapper.eq(GeoContentPlacementCite::getPlacementId, placementId)
                    .and(w -> w.isNull(GeoContentPlacementCite::getItemId));
        }
        if (excludeId != null) {
            wrapper.ne(GeoContentPlacementCite::getId, excludeId);
        }
        Long count = citeMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("同一提问问题在同一AI平台下已有引用记录，请勿重复添加");
        }
    }

    private void syncPlacementDerived(Long placementId) {
        List<GeoContentPlacementItem> items = itemMapper.selectList(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .eq(GeoContentPlacementItem::getPlacementId, placementId)
                .orderByAsc(GeoContentPlacementItem::getSortOrder)
                .orderByAsc(GeoContentPlacementItem::getId));
        String title = items.stream()
                .map(GeoContentPlacementItem::getTitle)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        String progress = aggregateStatus(items.stream().map(GeoContentPlacementItem::getPublishStatus).toList());
        GeoContentPlacement placement = requirePlacement(placementId);
        if (Constants.CONTENT_STATUS_QUEUE.equals(placement.getContentStatus())
                && Constants.CONTENT_AGG_NONE.equals(progress)) {
            progress = Constants.CONTENT_AGG_PENDING;
        }
        placement.setTitle(title);
        placement.setPlacementProgress(progress);
        placementMapper.updateById(placement);
        syncPublishedFromItems(requirePlacement(placementId));
    }

    private int nextItemSort(Long placementId) {
        GeoContentPlacementItem last = itemMapper.selectOne(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .eq(GeoContentPlacementItem::getPlacementId, placementId)
                .orderByDesc(GeoContentPlacementItem::getSortOrder)
                .last("LIMIT 1"));
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private int nextCiteSort(Long placementId) {
        GeoContentPlacementCite last = citeMapper.selectOne(new LambdaQueryWrapper<GeoContentPlacementCite>()
                .eq(GeoContentPlacementCite::getPlacementId, placementId)
                .orderByDesc(GeoContentPlacementCite::getSortOrder)
                .last("LIMIT 1"));
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private static String normalizePublishStatus(String raw) {
        String status = nz(raw);
        if (Constants.CONTENT_PUBLISH_SUCCESS.equals(status)
                || Constants.CONTENT_PUBLISH_REJECTED.equals(status)
                || Constants.CONTENT_PUBLISH_PENDING.equals(status)
                || Constants.CONTENT_PUBLISH_NONE.equals(status)) {
            return status;
        }
        return Constants.CONTENT_PUBLISH_NONE;
    }

    private static void assertPublishUrl(String raw) {
        String url = nz(raw);
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("请填写投放链接");
        }
        if (url.chars().anyMatch(Character::isWhitespace) || !url.matches("(?i)https?://\\S+")) {
            throw new BusinessException("投放链接必须是 http 或 https 链接");
        }
    }

    private static String pendingProofPlacementSql() {
        return """
                SELECT DISTINCT t.biz_id FROM sys_task t
                INNER JOIN sys_task_file f ON f.task_id = t.id AND f.is_active = 1
                WHERE t.biz_type = 'geo_content_placement' AND t.is_active = 1 AND t.biz_id IS NOT NULL
                UNION
                SELECT DISTINCT biz_id FROM sys_task_file
                WHERE biz_type = 'geo_content_placement' AND is_active = 1 AND biz_id IS NOT NULL
                """;
    }

    private void clearChildren(Long placementId) {
        citeMapper.delete(new LambdaQueryWrapper<GeoContentPlacementCite>()
                .eq(GeoContentPlacementCite::getPlacementId, placementId));
        itemMapper.delete(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .eq(GeoContentPlacementItem::getPlacementId, placementId));
    }

    private GeoContentPlacement findByBizKey(String publisher, String owner, String topic,
                                            String targetQuestion, String title) {
        return placementMapper.selectOne(new LambdaQueryWrapper<GeoContentPlacement>()
                .eq(GeoContentPlacement::getPublisherName, nz(publisher))
                .eq(GeoContentPlacement::getOwnerName, nz(owner))
                .eq(GeoContentPlacement::getTopicName, nz(topic))
                .eq(GeoContentPlacement::getTargetQuestion, nz(targetQuestion))
                .eq(GeoContentPlacement::getTitle, nz(title))
                .last("LIMIT 1"));
    }

    private GeoContentPlacementListVO toListVo(GeoContentPlacement entity,
                                              List<GeoContentPlacementItem> items,
                                              int citeCount) {
        int success = 0;
        for (GeoContentPlacementItem item : items) {
            if (Constants.CONTENT_PUBLISH_SUCCESS.equals(item.getPublishStatus())) {
                success++;
            }
        }
        GeoContentPlacementListVO vo = new GeoContentPlacementListVO();
        vo.setId(entity.getId());
        vo.setPublisherUserId(entity.getPublisherUserId());
        vo.setPublisherName(entity.getPublisherName());
        vo.setOwnerUserId(entity.getOwnerUserId());
        vo.setOwnerName(entity.getOwnerName());
        vo.setTopicId(entity.getTopicId());
        vo.setTopicName(entity.getTopicName());
        vo.setTargetQuestion(entity.getTargetQuestion());
        vo.setTitle(resolveDisplayTitle(entity, items));
        vo.setSource(entity.getSource());
        vo.setSourcePlacementId(entity.getSourcePlacementId());
        vo.setSourceAiModel(entity.getSourceAiModel());
        vo.setRemark(entity.getRemark());
        vo.setPlatformCount(items.size());
        vo.setSuccessCount(success);
        vo.setCiteCount(citeCount);
        vo.setProofFileCount(0);
        vo.setContentStatus(StringUtils.hasText(entity.getContentStatus())
                ? entity.getContentStatus()
                : Constants.CONTENT_STATUS_DRAFTING);
        vo.setContentTitle(entity.getContentTitle());
        String progress = StringUtils.hasText(entity.getPlacementProgress())
                ? entity.getPlacementProgress()
                : aggregateStatus(items.stream().map(GeoContentPlacementItem::getPublishStatus).toList());
        if (Constants.CONTENT_STATUS_QUEUE.equals(entity.getContentStatus())
                && (Constants.CONTENT_AGG_NONE.equals(progress) || !StringUtils.hasText(progress))) {
            progress = Constants.CONTENT_AGG_PENDING;
        }
        vo.setAggregateStatus(progress);
        vo.setPublishProgress(publishProgress(items.size(), success));
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private static void applyPendingIfNeeded(GeoContentPlacementListVO vo) {
        int proof = vo.getProofFileCount() == null ? 0 : vo.getProofFileCount();
        if (proof > 0 && Constants.CONTENT_AGG_NONE.equals(vo.getAggregateStatus())) {
            vo.setAggregateStatus(Constants.CONTENT_AGG_PENDING);
        }
    }

    private GeoContentPlacementArticleListVO toArticleListVo(
            GeoContentPlacementItem item, GeoContentPlacement placement, int citeCount) {
        GeoContentPlacementArticleListVO vo = new GeoContentPlacementArticleListVO();
        vo.setId(item.getId());
        vo.setPlacementId(item.getPlacementId());
        vo.setTitle(resolveItemDisplayTitle(item, placement));
        vo.setPlatformName(item.getPlatformName());
        vo.setContentForm(normalizeContentForm(item.getContentForm(), item.getPlatformName()));
        vo.setPublishStatus(item.getPublishStatus());
        vo.setPublishUrl(item.getPublishUrl());
        vo.setPublishTime(item.getPublishTime());
        vo.setRemark(item.getRemark());
        vo.setCiteCount(citeCount);
        vo.setCreateTime(item.getCreateTime());
        if (placement != null) {
            vo.setPublisherUserId(placement.getPublisherUserId());
            vo.setPublisherName(placement.getPublisherName());
            vo.setOwnerUserId(placement.getOwnerUserId());
            vo.setOwnerName(placement.getOwnerName());
            vo.setTopicId(placement.getTopicId());
            vo.setTopicName(placement.getTopicName());
            vo.setTargetQuestion(placement.getTargetQuestion());
        }
        return vo;
    }

    /**
     * 文章列表标题与维护投放一致：优先平台行 title（发布详情手填），
     * 再回退独立稿 contentTitle / 统一稿投放 contentTitle / 投放 title。
     */
    private static String resolveItemDisplayTitle(GeoContentPlacementItem item, GeoContentPlacement placement) {
        if (item != null && StringUtils.hasText(item.getTitle())) {
            return item.getTitle().trim();
        }
        boolean custom = item != null && Constants.CONTENT_MODE_CUSTOM.equals(normalizeContentMode(item.getContentMode()));
        if (custom && StringUtils.hasText(item.getContentTitle())) {
            return item.getContentTitle().trim();
        }
        if (placement != null && StringUtils.hasText(placement.getContentTitle())) {
            return placement.getContentTitle().trim();
        }
        if (placement != null && StringUtils.hasText(placement.getTitle())) {
            return placement.getTitle().trim();
        }
        return "";
    }

    /** 批量统计附件数：按 fileUrl 去重（完成复制到关联任务后业务侧不重复计数） */
    private Map<Long, Integer> loadProofFileCounts(List<Long> placementIds) {
        if (placementIds == null || placementIds.isEmpty()) {
            return Map.of();
        }
        String bizType = Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT;
        Map<Long, Set<String>> urlsByPlacement = new HashMap<>();

        for (SysTaskFile f : taskFileMapper.selectList(new LambdaQueryWrapper<SysTaskFile>()
                .eq(SysTaskFile::getBizType, bizType)
                .in(SysTaskFile::getBizId, placementIds)
                .select(SysTaskFile::getId, SysTaskFile::getBizId, SysTaskFile::getFileUrl))) {
            if (f.getBizId() == null) {
                continue;
            }
            urlsByPlacement.computeIfAbsent(f.getBizId(), k -> new HashSet<>()).add(proofFileDedupeKey(f));
        }

        List<SysTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getBizType, bizType)
                .in(SysTask::getBizId, placementIds)
                .select(SysTask::getId, SysTask::getBizId));
        if (!tasks.isEmpty()) {
            Map<Long, Long> taskToPlacement = tasks.stream()
                    .filter(t -> t.getId() != null && t.getBizId() != null)
                    .collect(Collectors.toMap(SysTask::getId, SysTask::getBizId, (a, b) -> a));
            List<Long> taskIds = new ArrayList<>(taskToPlacement.keySet());
            if (!taskIds.isEmpty()) {
                for (SysTaskFile f : taskFileMapper.selectList(new LambdaQueryWrapper<SysTaskFile>()
                        .in(SysTaskFile::getTaskId, taskIds)
                        .select(SysTaskFile::getId, SysTaskFile::getTaskId, SysTaskFile::getFileUrl))) {
                    Long placementId = taskToPlacement.get(f.getTaskId());
                    if (placementId == null) {
                        continue;
                    }
                    urlsByPlacement.computeIfAbsent(placementId, k -> new HashSet<>()).add(proofFileDedupeKey(f));
                }
            }
        }

        Map<Long, Integer> counts = new HashMap<>();
        for (Long id : placementIds) {
            counts.put(id, urlsByPlacement.getOrDefault(id, Set.of()).size());
        }
        return counts;
    }

    private static String resolveDisplayTitle(GeoContentPlacement entity, List<?> items) {
        // 优先正文标题，与撰写记录一致；再回退平台行标题 / 投放标题
        if (entity != null && StringUtils.hasText(entity.getContentTitle())) {
            return entity.getContentTitle().trim();
        }
        if (items != null) {
            for (Object row : items) {
                String title = null;
                if (row instanceof GeoContentPlacementItem item) {
                    title = item.getTitle();
                } else if (row instanceof GeoContentPlacementItemVO itemVo) {
                    title = itemVo.getTitle();
                }
                if (StringUtils.hasText(title)) {
                    return title.trim();
                }
            }
        }
        return entity == null ? null : entity.getTitle();
    }

    private static Integer publishProgress(int platformCount, int successCount) {
        if (platformCount <= 0) {
            return null;
        }
        return (int) Math.round(successCount * 100.0 / platformCount);
    }

    private GeoContentPlacementCiteVO toCiteVo(GeoContentPlacementCite cite) {
        GeoContentPlacementCiteVO vo = new GeoContentPlacementCiteVO();
        vo.setId(cite.getId());
        vo.setPlacementId(cite.getPlacementId());
        vo.setItemId(cite.getItemId());
        vo.setAskQuestion(cite.getAskQuestion());
        vo.setAiPlatform(cite.getAiPlatform());
        vo.setCiteUrl(cite.getCiteUrl());
        vo.setScreenshotUrl(fileStorageService.toPublicUrl(cite.getScreenshotUrl()));
        vo.setRemark(cite.getRemark());
        return vo;
    }

    static String aggregateStatus(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Constants.CONTENT_AGG_NONE;
        }
        int success = 0;
        int pending = 0;
        for (String status : statuses) {
            if (Constants.CONTENT_PUBLISH_SUCCESS.equals(status)) {
                success++;
            } else if (Constants.CONTENT_PUBLISH_PENDING.equals(status)) {
                pending++;
            }
        }
        if (success == statuses.size()) {
            return Constants.CONTENT_AGG_DONE;
        }
        if (success > 0) {
            return Constants.CONTENT_AGG_PARTIAL;
        }
        if (pending > 0) {
            return Constants.CONTENT_AGG_PENDING;
        }
        return Constants.CONTENT_AGG_NONE;
    }

    private GeoContentPublisherWeekRowVO buildPublisherWeekRow(
            Long publisherId, String publisherName, LocalDate weekStart, LocalDate weekEnd,
            List<GeoContentPlacementItem> items) {
        int produced = 0;
        int pendingReview = 0;
        int published = 0;
        int pendingProduce = 0;
        int videoPublished = 0;
        int videoPendingReview = 0;
        for (GeoContentPlacementItem item : items) {
            String form = normalizeContentForm(item.getContentForm(), item.getPlatformName());
            String status = item.getPublishStatus();
            boolean inWeek = item.getPublishTime() != null
                    && !item.getPublishTime().toLocalDate().isBefore(weekStart)
                    && !item.getPublishTime().toLocalDate().isAfter(weekEnd);
            boolean article = Constants.CONTENT_FORM_ARTICLE.equals(form);
            boolean video = Constants.CONTENT_FORM_VIDEO.equals(form);

            if (Constants.CONTENT_PUBLISH_NONE.equals(status)) {
                pendingProduce++;
            }
            if (Constants.CONTENT_PUBLISH_REJECTED.equals(status)) {
                if (article) {
                    pendingReview++;
                }
                if (video) {
                    videoPendingReview++;
                }
            }
            if (inWeek && !Constants.CONTENT_PUBLISH_NONE.equals(status) && article) {
                produced++;
            }
            if (inWeek && Constants.CONTENT_PUBLISH_SUCCESS.equals(status) && article) {
                published++;
            }
            if (inWeek && Constants.CONTENT_PUBLISH_SUCCESS.equals(status) && video) {
                videoPublished++;
            }
        }
        GeoContentPublisherWeekRowVO row = new GeoContentPublisherWeekRowVO();
        row.setWeekLabel(isoWeekLabel(weekStart));
        row.setWeekStart(weekStart.toString());
        row.setWeekEnd(weekEnd.toString());
        row.setPublisherUserId(publisherId);
        row.setPublisherName(publisherName);
        row.setProducedCount(produced);
        row.setPendingReviewCount(pendingReview);
        row.setHasPendingReview(pendingReview > 0);
        row.setPublishedCount(published);
        row.setPendingProduceCount(pendingProduce);
        row.setVideoPublishedCount(videoPublished);
        row.setVideoPendingReviewCount(videoPendingReview);
        row.setHasVideoPendingReview(videoPendingReview > 0);
        return row;
    }

    private boolean matchMetric(GeoContentPlacementItem item, String metric, LocalDate start, LocalDate end) {
        String form = normalizeContentForm(item.getContentForm(), item.getPlatformName());
        String status = item.getPublishStatus();
        boolean inWeek = item.getPublishTime() != null
                && !item.getPublishTime().toLocalDate().isBefore(start)
                && !item.getPublishTime().toLocalDate().isAfter(end);
        boolean article = Constants.CONTENT_FORM_ARTICLE.equals(form);
        boolean video = Constants.CONTENT_FORM_VIDEO.equals(form);
        return switch (metric) {
            case "produced" -> inWeek && article && !Constants.CONTENT_PUBLISH_NONE.equals(status);
            case "pendingReview" -> article && Constants.CONTENT_PUBLISH_REJECTED.equals(status);
            case "published" -> inWeek && article && Constants.CONTENT_PUBLISH_SUCCESS.equals(status);
            case "pendingProduce" -> Constants.CONTENT_PUBLISH_NONE.equals(status);
            case "videoPublished" -> inWeek && video && Constants.CONTENT_PUBLISH_SUCCESS.equals(status);
            case "videoPendingReview" -> video && Constants.CONTENT_PUBLISH_REJECTED.equals(status);
            default -> false;
        };
    }

    private GeoContentPublisherWeekDetailVO toWeekDetailVo(GeoContentPlacement placement, GeoContentPlacementItem item) {
        GeoContentPublisherWeekDetailVO vo = new GeoContentPublisherWeekDetailVO();
        vo.setItemId(item.getId());
        vo.setPlacementId(placement.getId());
        vo.setPublisherName(placement.getPublisherName());
        vo.setTopicName(placement.getTopicName());
        vo.setTargetQuestion(placement.getTargetQuestion());
        vo.setTitle(item.getTitle());
        vo.setPlatformName(item.getPlatformName());
        vo.setContentForm(normalizeContentForm(item.getContentForm(), item.getPlatformName()));
        vo.setPublishStatus(item.getPublishStatus());
        vo.setPublishUrl(item.getPublishUrl());
        vo.setPublishTime(item.getPublishTime());
        return vo;
    }

    private static List<LocalDate[]> splitIsoWeeks(LocalDate start, LocalDate end) {
        LocalDate cursor = start.with(DayOfWeek.MONDAY);
        if (cursor.isAfter(start)) {
            cursor = cursor.minusWeeks(1);
        }
        List<LocalDate[]> weeks = new ArrayList<>();
        while (!cursor.isAfter(end)) {
            LocalDate weekStart = cursor;
            LocalDate weekEnd = cursor.plusDays(6);
            weeks.add(new LocalDate[]{weekStart, weekEnd});
            cursor = cursor.plusWeeks(1);
        }
        return weeks;
    }

    private static String isoWeekLabel(LocalDate date) {
        WeekFields wf = WeekFields.ISO;
        int week = date.get(wf.weekOfWeekBasedYear());
        int year = date.get(wf.weekBasedYear());
        return "%d-W%02d".formatted(year, week);
    }

    private static LocalDate parseRequiredDate(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(label + "不能为空");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            throw new BusinessException(label + "格式错误，应为 yyyy-MM-dd");
        }
    }

    private static String normalizeMetric(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("指标类型不能为空");
        }
        String metric = raw.trim();
        return switch (metric) {
            case "produced", "pendingReview", "published", "pendingProduce", "videoPublished", "videoPendingReview" -> metric;
            case "产出", "产出篇数" -> "produced";
            case "待审", "有无待审" -> "pendingReview";
            case "已发布", "发布了多少篇" -> "published";
            case "待产出", "目前待产出" -> "pendingProduce";
            case "视频发布", "视频已发布" -> "videoPublished";
            case "视频待审" -> "videoPendingReview";
            default -> throw new BusinessException("不支持的指标类型: " + metric);
        };
    }

    private static String normalizeContentForm(String raw, String platformName) {
        if (Constants.CONTENT_FORM_VIDEO.equals(raw) || Constants.CONTENT_FORM_ARTICLE.equals(raw)) {
            return raw;
        }
        return inferContentForm(platformName);
    }

    private static String inferContentForm(String platformName) {
        String name = nz(platformName).toLowerCase(Locale.ROOT);
        if (name.contains("bilibili") || name.contains("哔哩") || name.contains("抖音")
                || name.contains("快手") || name.contains("视频号") || name.contains("视频")) {
            return Constants.CONTENT_FORM_VIDEO;
        }
        return Constants.CONTENT_FORM_ARTICLE;
    }

    private static String normalizeProgressFilter(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        return switch (value) {
            case "投放完成", "全部投放成功" -> Constants.CONTENT_AGG_DONE;
            case "部分投放", "部分投放成功" -> Constants.CONTENT_AGG_PARTIAL;
            case "未投放", "全部未投放" -> Constants.CONTENT_AGG_NONE;
            case "待投放" -> Constants.CONTENT_AGG_PENDING;
            default -> value;
        };
    }

    private static ParsedPublish parsePublish(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(text) || "未投放".equals(text)) {
            return new ParsedPublish(Constants.CONTENT_PUBLISH_NONE, "");
        }
        if (text.contains("审核未通过") || text.contains("未通过")) {
            return new ParsedPublish(Constants.CONTENT_PUBLISH_REJECTED, "");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return new ParsedPublish(Constants.CONTENT_PUBLISH_SUCCESS, text);
        }
        // 部分行把标题写进链接列，仍视为已投放但无有效 URL
        return new ParsedPublish(Constants.CONTENT_PUBLISH_SUCCESS, text);
    }

    private static LocalDateTime atStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private static String publishDayLabel(LocalDateTime time) {
        return time == null ? "-" : time.toLocalDate().toString();
    }

    private static String formatPublishTime(LocalDateTime time) {
        return time == null ? null : time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static LocalDate parseFlexibleDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim().replace('.', '/').replace('-', '/');
        String[] parts = text.split("/");
        try {
            if (parts.length >= 3) {
                int a = Integer.parseInt(parts[0].trim());
                int b = Integer.parseInt(parts[1].trim());
                int c = Integer.parseInt(parts[2].trim());
                // yyyy/M/d
                if (a >= 1000) {
                    return LocalDate.of(a, b, c);
                }
                // M/d/yy or M/d/yyyy
                int year = c < 100 ? 2000 + c : c;
                return LocalDate.of(year, a, b);
            }
        } catch (Exception ignored) {
            // fallthrough
        }
        return null;
    }

    private static String placementKey(String publisher, String owner, String topic,
                                       String targetQuestion, String title) {
        return nz(publisher) + "\0" + nz(owner) + "\0" + nz(topic) + "\0"
                + nz(targetQuestion) + "\0" + nz(title);
    }

    private static boolean isTemplateSample(String... parts) {
        if (parts == null) {
            return false;
        }
        for (String part : parts) {
            if (part != null && part.contains(GeoExcelTemplateWriter.SAMPLE_MARK)) {
                return true;
            }
        }
        return false;
    }

    private record CiteHeader(int col, String platform) {}

    /** 第2行从第11列起是 AI 平台名，直到「备注」列 */
    private static List<CiteHeader> readCiteHeaders(Sheet sheet) {
        int last = CITE_START_COL;
        if (sheet.getRow(0) != null) {
            last = Math.max(last, sheet.getRow(0).getLastCellNum());
        }
        if (sheet.getRow(1) != null) {
            last = Math.max(last, sheet.getRow(1).getLastCellNum());
        }
        List<CiteHeader> headers = new ArrayList<>();
        for (int col = CITE_START_COL; col < last; col++) {
            String top = ExcelCellUtils.str(sheet, 0, col);
            String name = ExcelCellUtils.str(sheet, 1, col);
            if (top.contains("备注") || name.contains("备注")) {
                break;
            }
            if (!StringUtils.hasText(name)) {
                break;
            }
            headers.add(new CiteHeader(col, name.trim()));
        }
        if (headers.isEmpty()) {
            headers.add(new CiteHeader(10, "豆包"));
            headers.add(new CiteHeader(11, "DS"));
            headers.add(new CiteHeader(12, "元宝"));
        }
        return headers;
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }

    private static InputStream openSeed(String classpath, String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpath);
        if (resource.exists()) {
            return resource.getInputStream();
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("GEO").resolve(fileName),
                cwd.resolve("../GEO").resolve(fileName).normalize(),
                cwd.resolve("../../GEO").resolve(fileName).normalize(),
                cwd.getParent() == null ? cwd : cwd.getParent().resolve("GEO").resolve(fileName)
        };
        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                return Files.newInputStream(path);
            }
        }
        throw new RuntimeException("找不到种子文件: " + fileName);
    }

    private record ParsedPublish(String status, String url) {}

    private LocalDate[] articleDateRange(GeoContentArticleBoardQueryDTO query) {
        LocalDate end = parseFlexibleDate(query == null ? null : query.getEndDate());
        LocalDate start = parseFlexibleDate(query == null ? null : query.getStartDate());
        if (end == null) {
            end = LocalDate.now();
        }
        if (start == null) {
            start = end.minusDays(27);
        }
        return new LocalDate[]{start, end};
    }

    private ArticleDataset loadArticleDataset(LocalDate start, LocalDate end, GeoContentArticleBoardQueryDTO query) {
        Set<Long> publisherFilter = new HashSet<>();
        if (query != null) {
            if (query.getPublisherUserId() != null) {
                publisherFilter.add(query.getPublisherUserId());
            }
            if (query.getPublisherUserIds() != null) {
                for (Long id : query.getPublisherUserIds()) {
                    if (id != null) {
                        publisherFilter.add(id);
                    }
                }
            }
        }
        List<String> publishPlatforms = query == null || query.getPublishPlatforms() == null ? List.of()
                : query.getPublishPlatforms().stream().filter(StringUtils::hasText).toList();
        List<String> aiPlatforms = query == null || query.getAiPlatforms() == null ? List.of()
                : query.getAiPlatforms().stream().filter(StringUtils::hasText).toList();
        String contentForm = query == null ? null : query.getContentForm();
        Long topicId = query == null ? null : query.getTopicId();

        LambdaQueryWrapper<GeoContentPlacement> placementWrapper = new LambdaQueryWrapper<GeoContentPlacement>()
                .eq(topicId != null, GeoContentPlacement::getTopicId, topicId)
                .in(!publisherFilter.isEmpty(), GeoContentPlacement::getPublisherUserId, publisherFilter);
        List<GeoContentPlacement> placements = placementMapper.selectList(placementWrapper);
        Map<Long, GeoContentPlacement> placementMap = placements.stream()
                .collect(Collectors.toMap(GeoContentPlacement::getId, p -> p, (a, b) -> a));
        if (placementMap.isEmpty()) {
            return ArticleDataset.empty();
        }

        List<GeoContentPlacementItem> items = itemMapper.selectList(new LambdaQueryWrapper<GeoContentPlacementItem>()
                .in(GeoContentPlacementItem::getPlacementId, placementMap.keySet())
                .ge(GeoContentPlacementItem::getPublishTime, start.atStartOfDay())
                .lt(GeoContentPlacementItem::getPublishTime, end.plusDays(1).atStartOfDay())
                .in(!publishPlatforms.isEmpty(), GeoContentPlacementItem::getPlatformName, publishPlatforms));
        List<ItemJoin> joins = new ArrayList<>();
        for (GeoContentPlacementItem item : items) {
            GeoContentPlacement placement = placementMap.get(item.getPlacementId());
            if (placement == null) {
                continue;
            }
            String form = normalizeContentForm(item.getContentForm(), item.getPlatformName());
            if (StringUtils.hasText(contentForm) && !contentForm.equals(form)) {
                continue;
            }
            joins.add(new ItemJoin(placement, item, form));
        }

        Set<Long> itemIds = joins.stream().map(j -> j.item.getId()).collect(Collectors.toSet());
        Set<Long> placementIds = joins.stream().map(j -> j.placement.getId()).collect(Collectors.toSet());
        List<CiteJoin> citeJoins = new ArrayList<>();
        Map<Long, Integer> citeCountByItem = new HashMap<>();
        if (!itemIds.isEmpty() || !placementIds.isEmpty()) {
            List<GeoContentPlacementCite> cites = citeMapper.selectList(new LambdaQueryWrapper<GeoContentPlacementCite>()
                    .and(w -> {
                        if (!itemIds.isEmpty()) {
                            w.in(GeoContentPlacementCite::getItemId, itemIds);
                        }
                        if (!placementIds.isEmpty()) {
                            if (!itemIds.isEmpty()) {
                                w.or();
                            }
                            w.in(GeoContentPlacementCite::getPlacementId, placementIds);
                        }
                    })
                    .in(!aiPlatforms.isEmpty(), GeoContentPlacementCite::getAiPlatform, aiPlatforms));
            Map<Long, ItemJoin> itemJoinMap = joins.stream()
                    .collect(Collectors.toMap(j -> j.item.getId(), j -> j, (a, b) -> a));
            for (GeoContentPlacementCite cite : cites) {
                ItemJoin itemJoin = cite.getItemId() == null ? null : itemJoinMap.get(cite.getItemId());
                GeoContentPlacement placement = itemJoin != null
                        ? itemJoin.placement
                        : placementMap.get(cite.getPlacementId());
                if (placement == null) {
                    continue;
                }
                GeoContentPlacementItem item = itemJoin != null ? itemJoin.item : null;
                if (item == null) {
                    // placement-level cite without item: skip platform filters already applied to items
                    continue;
                }
                if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(item.getPublishStatus())) {
                    continue;
                }
                if (item.getPublishTime() == null
                        || item.getPublishTime().toLocalDate().isBefore(start)
                        || item.getPublishTime().toLocalDate().isAfter(end)) {
                    continue;
                }
                citeJoins.add(new CiteJoin(placement, item, cite));
                citeCountByItem.merge(item.getId(), 1, Integer::sum);
            }
        }
        return new ArticleDataset(joins, citeJoins, citeCountByItem);
    }

    private List<GeoContentPublishAggRowVO> buildPublishAggRows(ArticleDataset data) {
        Map<String, GeoContentPublishAggRowVO> map = new LinkedHashMap<>();
        for (ItemJoin j : data.items) {
            if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(j.item.getPublishStatus())) {
                continue;
            }
            String date = publishDayLabel(j.item.getPublishTime());
            String key = date + "\0" + nz(j.placement.getTopicName()) + "\0"
                    + nz(j.placement.getPublisherName()) + "\0" + nz(j.item.getPlatformName()) + "\0" + j.form;
            GeoContentPublishAggRowVO row = map.computeIfAbsent(key, k -> {
                GeoContentPublishAggRowVO vo = new GeoContentPublishAggRowVO();
                vo.setDateLabel(date);
                vo.setTopicName(j.placement.getTopicName());
                vo.setPublisherName(j.placement.getPublisherName());
                vo.setPublisherUserId(j.placement.getPublisherUserId());
                vo.setPublishPlatform(j.item.getPlatformName());
                vo.setContentForm(j.form);
                vo.setPublishCount(0);
                return vo;
            });
            row.setPublishCount(row.getPublishCount() + 1);
        }
        return new ArrayList<>(map.values());
    }

    private List<GeoContentCiteAggRowVO> buildCiteAggRows(ArticleDataset data) {
        Map<String, int[]> bag = new LinkedHashMap<>();
        Map<String, String[]> meta = new LinkedHashMap<>();
        Set<Long> citedItems = data.cites.stream().map(c -> c.item.getId()).collect(Collectors.toSet());
        for (ItemJoin j : data.items) {
            if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(j.item.getPublishStatus())) {
                continue;
            }
            String date = publishDayLabel(j.item.getPublishTime());
            String key = date + "\0" + nz(j.placement.getTopicName()) + "\0"
                    + nz(j.placement.getPublisherName()) + "\0" + nz(j.item.getPlatformName()) + "\0*";
            bag.computeIfAbsent(key, k -> new int[2]);
            meta.putIfAbsent(key, new String[]{date, j.placement.getTopicName(), j.placement.getPublisherName(),
                    j.item.getPlatformName(), "全部"});
            bag.get(key)[0]++;
            if (citedItems.contains(j.item.getId())) {
                bag.get(key)[1]++;
            }
        }
        for (CiteJoin c : data.cites) {
            String date = publishDayLabel(c.item.getPublishTime());
            String key = date + "\0" + nz(c.placement.getTopicName()) + "\0"
                    + nz(c.placement.getPublisherName()) + "\0" + nz(c.item.getPlatformName())
                    + "\0" + nz(c.cite.getAiPlatform());
            // AI 平台维度仅统计命中次数用于图表，收录率仍以成功发布分母为准
            meta.putIfAbsent(key, new String[]{date, c.placement.getTopicName(), c.placement.getPublisherName(),
                    c.item.getPlatformName(), c.cite.getAiPlatform()});
            bag.putIfAbsent(key, new int[]{0, 0});
        }
        // 按 AI 平台：分母=该日该发布平台成功数，分子=该 AI 平台有引用的 item 数
        Map<String, Set<Long>> successByBase = new HashMap<>();
        for (ItemJoin j : data.items) {
            if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(j.item.getPublishStatus())) {
                continue;
            }
            String date = publishDayLabel(j.item.getPublishTime());
            String base = date + "\0" + nz(j.placement.getTopicName()) + "\0"
                    + nz(j.placement.getPublisherName()) + "\0" + nz(j.item.getPlatformName());
            successByBase.computeIfAbsent(base, k -> new HashSet<>()).add(j.item.getId());
        }
        Map<String, Set<Long>> citedByAi = new HashMap<>();
        for (CiteJoin c : data.cites) {
            String date = publishDayLabel(c.item.getPublishTime());
            String key = date + "\0" + nz(c.placement.getTopicName()) + "\0"
                    + nz(c.placement.getPublisherName()) + "\0" + nz(c.item.getPlatformName())
                    + "\0" + nz(c.cite.getAiPlatform());
            citedByAi.computeIfAbsent(key, k -> new HashSet<>()).add(c.item.getId());
        }

        List<GeoContentCiteAggRowVO> rows = new ArrayList<>();
        for (Map.Entry<String, int[]> e : bag.entrySet()) {
            String[] m = meta.get(e.getKey());
            GeoContentCiteAggRowVO vo = new GeoContentCiteAggRowVO();
            vo.setDateLabel(m[0]);
            vo.setTopicName(m[1]);
            vo.setPublisherName(m[2]);
            vo.setPublishPlatform(m[3]);
            vo.setAiPlatform(m[4]);
            if ("全部".equals(m[4])) {
                vo.setSuccessCount(e.getValue()[0]);
                vo.setCitedCount(e.getValue()[1]);
            } else {
                String base = m[0] + "\0" + nz(m[1]) + "\0" + nz(m[2]) + "\0" + nz(m[3]);
                int success = successByBase.getOrDefault(base, Set.of()).size();
                int cited = citedByAi.getOrDefault(e.getKey(), Set.of()).size();
                vo.setSuccessCount(success);
                vo.setCitedCount(cited);
            }
            vo.setCiteRate(vo.getSuccessCount() == 0 ? 0D
                    : Math.round(vo.getCitedCount() * 10000.0 / vo.getSuccessCount()) / 100.0);
            rows.add(vo);
        }
        return rows;
    }

    private List<GeoRankItemVO> buildPlatformCiteRank(ArticleDataset data) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (CiteJoin c : data.cites) {
            map.merge(StringUtils.hasText(c.item.getPlatformName()) ? c.item.getPlatformName() : "-", 1, Integer::sum);
        }
        return toRankList(map);
    }

    private List<GeoRankItemVO> buildArticleCiteRank(ArticleDataset data) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (CiteJoin c : data.cites) {
            map.merge(articleLabel(c.placement), 1, Integer::sum);
        }
        return toRankList(map);
    }

    private List<GeoRankItemVO> toRankList(Map<String, Integer> map) {
        return map.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(20)
                .map(e -> {
                    GeoRankItemVO vo = new GeoRankItemVO();
                    vo.setName(e.getKey());
                    vo.setValue(e.getValue());
                    return vo;
                })
                .toList();
    }

    private List<GeoContentPublisherCiteRowVO> buildPublisherCiteRows(
            ArticleDataset current, ArticleDataset mom, ArticleDataset yoy) {
        Map<Long, PublisherCiteBag> cur = publisherBags(current);
        Map<Long, PublisherCiteBag> momMap = publisherBags(mom);
        Map<Long, PublisherCiteBag> yoyMap = publisherBags(yoy);
        List<GeoContentPublisherCiteRowVO> rows = new ArrayList<>();
        for (Map.Entry<Long, PublisherCiteBag> e : cur.entrySet()) {
            PublisherCiteBag bag = e.getValue();
            PublisherCiteBag m = momMap.get(e.getKey());
            PublisherCiteBag y = yoyMap.get(e.getKey());
            GeoContentPublisherCiteRowVO vo = new GeoContentPublisherCiteRowVO();
            vo.setPublisherUserId(e.getKey());
            vo.setPublisherName(bag.name);
            vo.setSuccessCount(bag.success);
            vo.setCitedCount(bag.cited);
            vo.setCiteHitCount(bag.hits);
            vo.setCiteRate(rate(bag.cited, bag.success));
            vo.setCiteRateMom(m == null || m.success == 0 ? null : roundRate(vo.getCiteRate() - rate(m.cited, m.success)));
            vo.setCiteRateYoy(y == null || y.success == 0 ? null : roundRate(vo.getCiteRate() - rate(y.cited, y.success)));
            vo.setCiteHitCountMom(m == null ? null : bag.hits - m.hits);
            vo.setCiteHitCountYoy(y == null ? null : bag.hits - y.hits);
            rows.add(vo);
        }
        rows.sort((a, b) -> Integer.compare(
                b.getCiteHitCount() == null ? 0 : b.getCiteHitCount(),
                a.getCiteHitCount() == null ? 0 : a.getCiteHitCount()));
        return rows;
    }

    private Map<Long, PublisherCiteBag> publisherBags(ArticleDataset data) {
        Map<Long, PublisherCiteBag> map = new LinkedHashMap<>();
        Set<Long> citedItems = data.cites.stream().map(c -> c.item.getId()).collect(Collectors.toSet());
        for (ItemJoin j : data.items) {
            if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(j.item.getPublishStatus())) {
                continue;
            }
            Long id = j.placement.getPublisherUserId() == null ? -1L : j.placement.getPublisherUserId();
            PublisherCiteBag bag = map.computeIfAbsent(id, k -> {
                PublisherCiteBag b = new PublisherCiteBag();
                b.name = j.placement.getPublisherName();
                return b;
            });
            bag.success++;
            if (citedItems.contains(j.item.getId())) {
                bag.cited++;
            }
        }
        for (CiteJoin c : data.cites) {
            Long id = c.placement.getPublisherUserId() == null ? -1L : c.placement.getPublisherUserId();
            PublisherCiteBag bag = map.computeIfAbsent(id, k -> {
                PublisherCiteBag b = new PublisherCiteBag();
                b.name = c.placement.getPublisherName();
                return b;
            });
            bag.hits++;
        }
        return map;
    }

    private List<GeoChartPointVO> toPublishCountChart(List<GeoContentPublishAggRowVO> rows) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (GeoContentPublishAggRowVO row : rows) {
            String series = StringUtils.hasText(row.getPublishPlatform()) ? row.getPublishPlatform() : "未命名平台";
            String key = row.getDateLabel() + "\0" + series;
            map.merge(key, row.getPublishCount() == null ? 0 : row.getPublishCount(), Integer::sum);
        }
        return toChartPoints(map);
    }

    private List<GeoChartPointVO> toCiteRateChart(List<GeoContentCiteAggRowVO> rows) {
        List<GeoChartPointVO> points = new ArrayList<>();
        for (GeoContentCiteAggRowVO row : rows) {
            if (!"全部".equals(row.getAiPlatform())) {
                continue;
            }
            GeoChartPointVO p = new GeoChartPointVO();
            p.setAxis(row.getDateLabel());
            p.setSeries(StringUtils.hasText(row.getPublishPlatform()) ? row.getPublishPlatform() : "未命名平台");
            p.setValue(row.getCiteRate() == null ? 0D : row.getCiteRate());
            points.add(p);
        }
        return points;
    }

    private List<GeoChartPointVO> toPublisherCiteChart(List<GeoContentPublisherCiteRowVO> rows) {
        List<GeoChartPointVO> points = new ArrayList<>();
        for (GeoContentPublisherCiteRowVO row : rows) {
            GeoChartPointVO rate = new GeoChartPointVO();
            rate.setAxis(row.getPublisherName() == null ? "-" : row.getPublisherName());
            rate.setSeries("收录率%");
            rate.setValue(row.getCiteRate() == null ? 0D : row.getCiteRate());
            points.add(rate);
            GeoChartPointVO hits = new GeoChartPointVO();
            hits.setAxis(row.getPublisherName() == null ? "-" : row.getPublisherName());
            hits.setSeries("引用次数");
            hits.setValue(row.getCiteHitCount() == null ? 0D : row.getCiteHitCount().doubleValue());
            points.add(hits);
        }
        return points;
    }

    private List<GeoChartPointVO> toChartPoints(Map<String, Integer> map) {
        List<GeoChartPointVO> points = new ArrayList<>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            String[] parts = e.getKey().split("\0", 2);
            GeoChartPointVO p = new GeoChartPointVO();
            p.setAxis(parts[0]);
            p.setSeries(parts.length > 1 ? parts[1] : "-");
            p.setValue(e.getValue().doubleValue());
            points.add(p);
        }
        return points;
    }

    private GeoContentArticleDetailRowVO toDetailFromItem(ItemJoin j, int citeCount) {
        GeoContentArticleDetailRowVO vo = new GeoContentArticleDetailRowVO();
        vo.setPlacementId(j.placement.getId());
        vo.setItemId(j.item.getId());
        vo.setTargetQuestion(j.placement.getTargetQuestion());
        vo.setTitle(StringUtils.hasText(j.item.getTitle()) ? j.item.getTitle() : j.placement.getTitle());
        vo.setTopicName(j.placement.getTopicName());
        vo.setPublisherName(j.placement.getPublisherName());
        vo.setPublishPlatform(j.item.getPlatformName());
        vo.setContentForm(j.form);
        vo.setPublishStatus(j.item.getPublishStatus());
        vo.setPublishTime(formatPublishTime(j.item.getPublishTime()));
        vo.setPublishUrl(j.item.getPublishUrl());
        vo.setCiteCount(citeCount);
        return vo;
    }

    private GeoContentArticleDetailRowVO toDetailFromCite(CiteJoin c) {
        GeoContentArticleDetailRowVO vo = toDetailFromItem(
                new ItemJoin(c.placement, c.item, normalizeContentForm(c.item.getContentForm(), c.item.getPlatformName())),
                1);
        vo.setAiPlatform(c.cite.getAiPlatform());
        vo.setCiteUrl(c.cite.getCiteUrl());
        vo.setAskQuestion(c.cite.getAskQuestion());
        return vo;
    }

    private static String articleLabel(GeoContentPlacement placement) {
        if (StringUtils.hasText(placement.getTitle())) {
            return placement.getTitle();
        }
        if (StringUtils.hasText(placement.getTargetQuestion())) {
            return placement.getTargetQuestion();
        }
        return "投放#" + placement.getId();
    }

    private static double rate(int cited, int success) {
        if (success <= 0) {
            return 0D;
        }
        return Math.round(cited * 10000.0 / success) / 100.0;
    }

    private static Double roundRate(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class PublisherCiteBag {
        private String name;
        private int success;
        private int cited;
        private int hits;
    }

    private record ItemJoin(GeoContentPlacement placement, GeoContentPlacementItem item, String form) {}

    private record CiteJoin(GeoContentPlacement placement, GeoContentPlacementItem item, GeoContentPlacementCite cite) {}

    private record ArticleDataset(
            List<ItemJoin> items,
            List<CiteJoin> cites,
            Map<Long, Integer> citeCountByItem) {
        static ArticleDataset empty() {
            return new ArticleDataset(List.of(), List.of(), Map.of());
        }
    }

    @Override
    @Transactional
    public GeoPersistResultVO autoPersistCompletedWeekly(int lookbackWeeks) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        int weeks = Math.max(lookbackWeeks, 1);
        LocalDate start = end.minusWeeks(weeks - 1L).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return persistContentPeriods("WEEK", start, end, today);
    }

    @Override
    @Transactional
    public GeoPersistResultVO autoPersistCompletedMonthly(int lookbackMonths) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.with(TemporalAdjusters.firstDayOfMonth()).minusDays(1);
        int months = Math.max(lookbackMonths, 1);
        LocalDate start = end.minusMonths(months - 1L).with(TemporalAdjusters.firstDayOfMonth());
        return persistContentPeriods("MONTH", start, end, today);
    }

    @Override
    @Transactional
    public GeoPersistResultVO persistContentBoardRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return emptyPersistResult();
        }
        LocalDate today = LocalDate.now();
        GeoPersistResultVO week = persistContentPeriods("WEEK", start, end, today);
        GeoPersistResultVO month = persistContentPeriods("MONTH", start, end, today);
        GeoPersistResultVO merged = new GeoPersistResultVO();
        merged.setPeriodCount(nz(week.getPeriodCount()) + nz(month.getPeriodCount()));
        merged.setSnapshotCount(nz(week.getSnapshotCount()) + nz(month.getSnapshotCount()));
        merged.setLockedDailyCount(0);
        return merged;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private GeoPersistResultVO emptyPersistResult() {
        GeoPersistResultVO vo = new GeoPersistResultVO();
        vo.setPeriodCount(0);
        vo.setSnapshotCount(0);
        vo.setLockedDailyCount(0);
        return vo;
    }

    private GeoPersistResultVO persistContentPeriods(String periodType, LocalDate start, LocalDate end, LocalDate today) {
        GeoContentArticleBoardQueryDTO query = new GeoContentArticleBoardQueryDTO();
        query.setStartDate(start.toString());
        query.setEndDate(end.toString());
        ArticleDataset data = loadArticleDataset(start, end, query);
        Map<String, ContentPeriodBag> bags = new LinkedHashMap<>();
        Set<Long> citedItems = data.cites.stream().map(c -> c.item.getId()).collect(Collectors.toSet());
        for (ItemJoin j : data.items) {
            if (!Constants.CONTENT_PUBLISH_SUCCESS.equals(j.item.getPublishStatus()) || j.item.getPublishTime() == null) {
                continue;
            }
            LocalDate d = j.item.getPublishTime().toLocalDate();
            if (d.isBefore(start) || d.isAfter(end)) {
                continue;
            }
            LocalDate[] bounds = "WEEK".equals(periodType) ? weekBounds(d) : monthBounds(d);
            if (!bounds[1].isBefore(today)) {
                continue; // 未结束周期不落库
            }
            String periodKey = "WEEK".equals(periodType) ? weekKey(d) : monthKey(d);
            String periodLabel = "WEEK".equals(periodType) ? weekLabel(d) : monthLabel(d);
            long publisherId = j.placement.getPublisherUserId() == null ? 0L : j.placement.getPublisherUserId();
            long topicId = j.placement.getTopicId() == null ? 0L : j.placement.getTopicId();
            String key = periodType + "\0" + periodKey + "\0" + publisherId + "\0" + topicId + "\0*";
            ContentPeriodBag bag = bags.computeIfAbsent(key, k -> {
                ContentPeriodBag b = new ContentPeriodBag();
                b.periodType = periodType;
                b.periodKey = periodKey;
                b.periodLabel = periodLabel;
                b.periodStart = bounds[0];
                b.periodEnd = bounds[1];
                b.publisherUserId = publisherId;
                b.publisherName = nz(j.placement.getPublisherName());
                b.topicId = topicId;
                b.topicName = nz(j.placement.getTopicName());
                b.publishPlatform = "*";
                return b;
            });
            bag.success++;
            if (citedItems.contains(j.item.getId())) {
                bag.cited++;
            }
        }
        for (CiteJoin c : data.cites) {
            if (c.item.getPublishTime() == null) {
                continue;
            }
            LocalDate d = c.item.getPublishTime().toLocalDate();
            LocalDate[] bounds = "WEEK".equals(periodType) ? weekBounds(d) : monthBounds(d);
            if (!bounds[1].isBefore(today)) {
                continue;
            }
            String periodKey = "WEEK".equals(periodType) ? weekKey(d) : monthKey(d);
            long publisherId = c.placement.getPublisherUserId() == null ? 0L : c.placement.getPublisherUserId();
            long topicId = c.placement.getTopicId() == null ? 0L : c.placement.getTopicId();
            String key = periodType + "\0" + periodKey + "\0" + publisherId + "\0" + topicId + "\0*";
            ContentPeriodBag bag = bags.get(key);
            if (bag != null) {
                bag.hits++;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        int snapshot = 0;
        Set<String> periods = new HashSet<>();
        for (ContentPeriodBag bag : bags.values()) {
            upsertContentSnapshot(bag, now);
            snapshot++;
            periods.add(bag.periodKey);
        }
        GeoPersistResultVO result = new GeoPersistResultVO();
        result.setPeriodCount(periods.size());
        result.setSnapshotCount(snapshot);
        result.setLockedDailyCount(0);
        return result;
    }

    private void upsertContentSnapshot(ContentPeriodBag bag, LocalDateTime now) {
        GeoContentPeriodStat existing = contentPeriodStatMapper.selectOne(new LambdaQueryWrapper<GeoContentPeriodStat>()
                .eq(GeoContentPeriodStat::getPeriodType, bag.periodType)
                .eq(GeoContentPeriodStat::getPeriodKey, bag.periodKey)
                .eq(GeoContentPeriodStat::getPublisherUserId, bag.publisherUserId)
                .eq(GeoContentPeriodStat::getTopicId, bag.topicId)
                .eq(GeoContentPeriodStat::getPublishPlatform, bag.publishPlatform)
                .last("LIMIT 1"));
        GeoContentPeriodStat entity = existing == null ? new GeoContentPeriodStat() : existing;
        entity.setPeriodType(bag.periodType);
        entity.setPeriodKey(bag.periodKey);
        entity.setPeriodLabel(bag.periodLabel);
        entity.setPeriodStart(bag.periodStart);
        entity.setPeriodEnd(bag.periodEnd);
        entity.setPublisherUserId(bag.publisherUserId);
        entity.setPublisherName(bag.publisherName);
        entity.setTopicId(bag.topicId);
        entity.setTopicName(bag.topicName);
        entity.setPublishPlatform(bag.publishPlatform);
        entity.setSuccessCount(bag.success);
        entity.setCitedCount(bag.cited);
        entity.setCiteHitCount(bag.hits);
        entity.setCiteRate(BigDecimal.valueOf(rate(bag.cited, bag.success)));
        entity.setLockedAt(now);
        if (existing == null) {
            contentPeriodStatMapper.insert(entity);
        } else {
            contentPeriodStatMapper.updateById(entity);
        }
    }

    private static LocalDate[] weekBounds(LocalDate date) {
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return new LocalDate[]{start, end};
    }

    private static LocalDate[] monthBounds(LocalDate date) {
        return new LocalDate[]{date.with(TemporalAdjusters.firstDayOfMonth()), date.with(TemporalAdjusters.lastDayOfMonth())};
    }

    private static String weekKey(LocalDate date) {
        WeekFields wf = WeekFields.ISO;
        return date.get(wf.weekBasedYear()) + "-W" + String.format("%02d", date.get(wf.weekOfWeekBasedYear()));
    }

    private static String weekLabel(LocalDate date) {
        WeekFields wf = WeekFields.ISO;
        return date.get(wf.weekBasedYear()) + "年第" + date.get(wf.weekOfWeekBasedYear()) + "周";
    }

    private static String monthKey(LocalDate date) {
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    private static String monthLabel(LocalDate date) {
        return date.getYear() + "年" + date.getMonthValue() + "月";
    }

    private static class ContentPeriodBag {
        private String periodType;
        private String periodKey;
        private String periodLabel;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private long publisherUserId;
        private String publisherName;
        private long topicId;
        private String topicName;
        private String publishPlatform;
        private int success;
        private int cited;
        private int hits;
    }
}
