package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.Constants;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentPlacementCiteDTO;
import com.base.admin.domain.dto.GeoContentPlacementDTO;
import com.base.admin.domain.dto.GeoContentPlacementItemDTO;
import com.base.admin.domain.dto.GeoContentPlacementQueryDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoContentPlacementCite;
import com.base.admin.domain.entity.GeoContentPlacementItem;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.vo.GeoContentPlacementCiteVO;
import com.base.admin.domain.vo.GeoContentPlacementDetailVO;
import com.base.admin.domain.vo.GeoContentPlacementItemVO;
import com.base.admin.domain.vo.GeoContentPlacementListVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPlacementCiteMapper;
import com.base.admin.mapper.GeoContentPlacementItemMapper;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.AiChatService;
import com.base.admin.service.GeoContentPlacementService;
import com.base.admin.service.GeoTopicService;
import com.base.admin.util.ExcelCellUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoContentPlacementServiceImpl implements GeoContentPlacementService {

    private static final String[] AI_PLATFORMS = {"豆包", "DS", "元宝"};

    private final GeoContentPlacementMapper placementMapper;
    private final GeoContentPlacementItemMapper itemMapper;
    private final GeoContentPlacementCiteMapper citeMapper;
    private final SysUserMapper userMapper;
    private final GeoTopicService topicService;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

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
                .eq(StringUtils.hasText(filterAgg), GeoContentPlacement::getPlacementProgress, filterAgg)
                .orderByDesc(GeoContentPlacement::getId);

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
            if (entity.getSourcePlacementId() != null) {
                vo.setSourceTargetQuestion(sourceQuestionMap.get(entity.getSourcePlacementId()));
            }
            rows.add(vo);
        }
        return new PageResult<>(page.getTotal(), rows);
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
        if (entity.getSourcePlacementId() != null) {
            GeoContentPlacement source = placementMapper.selectById(entity.getSourcePlacementId());
            if (source != null) {
                detail.setSourceTargetQuestion(source.getTargetQuestion());
            }
        }
        detail.setRemark(entity.getRemark());
        detail.setItems(items);
        detail.setCites(cites);
        String progress = StringUtils.hasText(entity.getPlacementProgress())
                ? entity.getPlacementProgress()
                : aggregateStatus(items.stream().map(GeoContentPlacementItemVO::getPublishStatus).toList());
        detail.setAggregateStatus(progress);
        detail.setPublishProgress(publishProgress(items.size(),
                (int) items.stream().filter(i -> Constants.CONTENT_PUBLISH_SUCCESS.equals(i.getPublishStatus())).count()));
        return detail;
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
        placement.setTitle("");
        placement.setSource(Constants.CONTENT_SOURCE_MANUAL);
        placement.setSourcePlacementId(null);
        placement.setPlacementProgress(Constants.CONTENT_AGG_NONE);
        placementMapper.insert(placement);
        return placement.getId();
    }

    @Override
    @Transactional
    public void update(GeoContentPlacementDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("内容投放ID不能为空");
        }
        GeoContentPlacement placement = requirePlacement(dto.getId());
        fillPlacement(placement, dto);
        placementMapper.updateById(placement);
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
    }

    @Override
    @Transactional
    public List<GeoContentPlacementListVO> generateSimilar(Long placementId) {
        GeoContentPlacement source = requirePlacement(placementId);
        if (!StringUtils.hasText(source.getTargetQuestion())) {
            throw new BusinessException("当前记录缺少目标问题，无法生成相似问题");
        }
        List<String> questions = generateSimilarQuestions(source);
        if (questions.isEmpty()) {
            throw new BusinessException("未能生成相似问题，请稍后重试");
        }
        List<GeoContentPlacementListVO> created = new ArrayList<>();
        for (String question : questions) {
            GeoContentPlacement row = new GeoContentPlacement();
            row.setPublisherUserId(null);
            row.setPublisherName(Constants.CONTENT_UNASSIGNED);
            row.setOwnerUserId(null);
            row.setOwnerName(Constants.CONTENT_UNASSIGNED);
            // 话题跟随源记录；仅有名称时自动建档关联
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
            row.setPlacementProgress(Constants.CONTENT_AGG_NONE);
            row.setRemark("由「" + source.getTargetQuestion() + "」AI生成，待分配发布人/归属人");
            placementMapper.insert(row);
            created.add(toListVo(row, List.of(), 0));
        }
        return created;
    }

    private List<String> generateSimilarQuestions(GeoContentPlacement source) {
        String topic = nz(source.getTopicName());
        String question = source.getTargetQuestion().trim();
        if (aiChatService.isEnabled()) {
            try {
                String system = """
                        你是 GEO 内容投放助手。请围绕给定目标问题，生成 3 到 5 条语义相近但表述不同的「待投放目标问题」。
                        要求：
                        1. 只输出 JSON 数组，例如 ["问题1","问题2"]，不要 markdown，不要解释；
                        2. 问题要适合中文内容投放，口语化、可检索；
                        3. 不要与原问题完全相同，也不要重复。
                        """;
                String user = "话题：" + (topic.isEmpty() ? "未指定" : topic) + "\n原目标问题：" + question;
                String content = aiChatService.chat(system, user);
                List<String> parsed = parseQuestionArray(content);
                if (!parsed.isEmpty()) {
                    return parsed.stream().filter(q -> !q.equals(question)).distinct().limit(5).toList();
                }
            } catch (Exception e) {
                log.warn("AI 生成相似问题失败，改用本地启发式: {}", e.getMessage());
            }
        }
        return heuristicSimilarQuestions(topic, question);
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

    private List<String> heuristicSimilarQuestions(String topic, String question) {
        String base = question.replaceAll("[？?！!。]$", "").trim();
        String topicPrefix = StringUtils.hasText(topic) ? topic : "相关场景";
        List<String> templates = List.of(
                "2026 年" + base + "有哪些靠谱推荐？",
                base + "怎么选更划算？",
                "第一次了解" + topicPrefix + "，" + base + "该怎么入手？",
                "对比同类产品后，" + base + "更推荐哪类？",
                "送礼场景下，" + base + "怎么挑更体面？"
        );
        return templates.stream()
                .map(String::trim)
                .filter(q -> !q.equals(question))
                .distinct()
                .limit(4)
                .toList();
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
        return item.getId();
    }

    @Override
    @Transactional
    public void updateItem(GeoContentPlacementItemDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("发布详情ID不能为空");
        }
        GeoContentPlacementItem item = requireItem(dto.getId());
        if (!item.getPlacementId().equals(dto.getPlacementId())) {
            throw new BusinessException("发布详情与内容投放不匹配");
        }
        fillItem(item, dto);
        itemMapper.updateById(item);
        syncPlacementDerived(item.getPlacementId());
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
    public Long createCite(GeoContentPlacementCiteDTO dto) {
        requirePlacement(dto.getPlacementId());
        if (dto.getItemId() != null) {
            GeoContentPlacementItem item = requireItem(dto.getItemId());
            if (!item.getPlacementId().equals(dto.getPlacementId())) {
                throw new BusinessException("引用与发布详情不匹配");
            }
        }
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
        GeoImportResultVO result = new GeoImportResultVO();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Long currentPlacementId = null;
            Long lastItemId = null;
            int itemSort = 0;
            int citeSort = 0;
            Set<String> dedupeCites = new HashSet<>();
            Map<String, Long> placementKeyCache = new LinkedHashMap<>();

            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
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
                String doubao = ExcelCellUtils.rawStr(sheet, r, 10);
                String ds = ExcelCellUtils.rawStr(sheet, r, 11);
                String yuanbao = ExcelCellUtils.rawStr(sheet, r, 12);
                String remark = ExcelCellUtils.str(sheet, r, 13);

                boolean newGroup = StringUtils.hasText(seq);
                boolean hasPlatform = StringUtils.hasText(platform);
                boolean hasAsk = StringUtils.hasText(askQuestion);
                boolean hasCiteUrl = StringUtils.hasText(doubao) || StringUtils.hasText(ds) || StringUtils.hasText(yuanbao);

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
                }

                if (currentPlacementId == null) {
                    continue;
                }

                if (hasPlatform) {
                    GeoContentPlacementItem item = new GeoContentPlacementItem();
                    item.setPlacementId(currentPlacementId);
                    item.setTitle(nz(title));
                    item.setPlatformName(platform.trim());
                    ParsedPublish parsed = parsePublish(linkOrStatus);
                    item.setPublishStatus(parsed.status());
                    item.setPublishUrl(parsed.url());
                    item.setPublishTime(parseFlexibleDate(publishTimeRaw));
                    item.setSortOrder(itemSort++);
                    item.setRemark("");
                    itemMapper.insert(item);
                    lastItemId = item.getId();
                }

                if (hasAsk && hasCiteUrl) {
                    String[] urls = {doubao, ds, yuanbao};
                    for (int i = 0; i < AI_PLATFORMS.length; i++) {
                        String url = urls[i];
                        if (!StringUtils.hasText(url)) {
                            continue;
                        }
                        String citeKey = askQuestion.trim() + "\0" + AI_PLATFORMS[i] + "\0" + url.trim();
                        if (!dedupeCites.add(citeKey)) {
                            continue;
                        }
                        GeoContentPlacementCite cite = new GeoContentPlacementCite();
                        cite.setPlacementId(currentPlacementId);
                        cite.setItemId(lastItemId);
                        cite.setAskQuestion(askQuestion.trim());
                        cite.setAiPlatform(AI_PLATFORMS[i]);
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
            throw new BusinessException("导入内容投放失败: " + e.getMessage());
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
        placement.setTargetQuestion(nz(dto.getTargetQuestion()));
        placement.setRemark(dto.getRemark());
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
            placement.setPublisherUserId(null);
            placement.setPublisherName(Constants.CONTENT_UNASSIGNED);
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
                placement.setPublisherUserId(null);
                placement.setPublisherName(Constants.CONTENT_UNASSIGNED);
            } else {
                placement.setOwnerUserId(null);
                placement.setOwnerName(Constants.CONTENT_UNASSIGNED);
            }
            return;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Objects.equals(user.getStatus(), Constants.STATUS_ACTIVE)) {
            throw new BusinessException((publisher ? "发布人" : "归属人") + "用户不存在或已停用");
        }
        if (publisher) {
            placement.setPublisherUserId(user.getUserId());
            placement.setPublisherName(userDisplayName(user));
        } else {
            placement.setOwnerUserId(user.getUserId());
            placement.setOwnerName(userDisplayName(user));
        }
    }

    private void applyTopic(GeoContentPlacement placement, Long topicId, String topicName) {
        GeoTopic topic = null;
        if (topicId != null) {
            topic = topicService.getById(topicId);
        } else if (StringUtils.hasText(topicName)) {
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
        item.setPublishStatus(normalizePublishStatus(dto.getPublishStatus()));
        item.setPublishUrl(nz(dto.getPublishUrl()));
        item.setPublishTime(dto.getPublishTime());
        item.setSortOrder(dto.getSortOrder());
        item.setRemark(dto.getRemark());
    }

    private void fillCite(GeoContentPlacementCite cite, GeoContentPlacementCiteDTO dto) {
        cite.setPlacementId(dto.getPlacementId());
        cite.setItemId(dto.getItemId());
        cite.setAskQuestion(nz(dto.getAskQuestion()));
        cite.setAiPlatform(nz(dto.getAiPlatform()));
        cite.setCiteUrl(nz(dto.getCiteUrl()));
        cite.setSortOrder(dto.getSortOrder());
        cite.setRemark(dto.getRemark());
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
        placement.setTitle(title);
        placement.setPlacementProgress(progress);
        placementMapper.updateById(placement);
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
                || Constants.CONTENT_PUBLISH_NONE.equals(status)) {
            return status;
        }
        return Constants.CONTENT_PUBLISH_NONE;
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
        vo.setRemark(entity.getRemark());
        vo.setPlatformCount(items.size());
        vo.setSuccessCount(success);
        vo.setCiteCount(citeCount);
        String progress = StringUtils.hasText(entity.getPlacementProgress())
                ? entity.getPlacementProgress()
                : aggregateStatus(items.stream().map(GeoContentPlacementItem::getPublishStatus).toList());
        vo.setAggregateStatus(progress);
        vo.setPublishProgress(publishProgress(items.size(), success));
        return vo;
    }

    private static String resolveDisplayTitle(GeoContentPlacement entity, List<?> items) {
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
        return entity.getTitle();
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
        vo.setRemark(cite.getRemark());
        return vo;
    }

    static String aggregateStatus(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Constants.CONTENT_AGG_NONE;
        }
        int success = 0;
        for (String status : statuses) {
            if (Constants.CONTENT_PUBLISH_SUCCESS.equals(status)) {
                success++;
            }
        }
        if (success == 0) {
            return Constants.CONTENT_AGG_NONE;
        }
        if (success == statuses.size()) {
            return Constants.CONTENT_AGG_DONE;
        }
        return Constants.CONTENT_AGG_PARTIAL;
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
}
