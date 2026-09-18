package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.Constants;
import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentArticleBoardQueryDTO;
import com.base.admin.domain.dto.GeoContentArticleDetailQueryDTO;
import com.base.admin.domain.dto.GeoContentPlacementCiteDTO;
import com.base.admin.domain.dto.GeoContentPlacementDTO;
import com.base.admin.domain.dto.GeoContentPlacementItemDTO;
import com.base.admin.domain.dto.GeoContentPlacementQueryDTO;
import com.base.admin.domain.dto.GeoContentPublisherWeekDetailQueryDTO;
import com.base.admin.domain.dto.GeoContentPublisherWeekQueryDTO;
import com.base.admin.domain.entity.GeoContentPeriodStat;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoContentPlacementCite;
import com.base.admin.domain.entity.GeoContentPlacementItem;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskFile;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.GeoChartPointVO;
import com.base.admin.domain.vo.GeoContentArticleBoardVO;
import com.base.admin.domain.vo.GeoContentArticleDetailRowVO;
import com.base.admin.domain.vo.GeoContentCiteAggRowVO;
import com.base.admin.domain.vo.GeoContentPlacementCiteVO;
import com.base.admin.domain.vo.GeoContentPlacementDetailVO;
import com.base.admin.domain.vo.GeoContentPlacementItemVO;
import com.base.admin.domain.vo.GeoContentPlacementListVO;
import com.base.admin.domain.vo.GeoContentPublishAggRowVO;
import com.base.admin.domain.vo.GeoContentPublisherCiteRowVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekBoardVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekDetailVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekRowVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoPersistResultVO;
import com.base.admin.domain.vo.GeoRankItemVO;
import com.base.admin.domain.vo.SysTaskFileVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPeriodStatMapper;
import com.base.admin.mapper.GeoContentPlacementCiteMapper;
import com.base.admin.mapper.GeoContentPlacementItemMapper;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.SysTaskFileMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.AiChatService;
import com.base.admin.service.GeoContentPlacementService;
import com.base.admin.service.GeoTopicService;
import com.base.admin.service.PlacementTaskSyncService;
import com.base.admin.service.SysAiProviderService;
import com.base.admin.util.ExcelCellUtils;
import com.base.admin.util.GeoExcelTemplateWriter;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoContentPlacementServiceImpl implements GeoContentPlacementService {

    private static final int CITE_START_COL = 10;

    private final GeoContentPlacementMapper placementMapper;
    private final GeoContentPlacementItemMapper itemMapper;
    private final GeoContentPlacementCiteMapper citeMapper;
    private final GeoContentPeriodStatMapper contentPeriodStatMapper;
    private final SysUserMapper userMapper;
    private final GeoTopicService topicService;
    private final AiChatService aiChatService;
    private final SysAiProviderService sysAiProviderService;
    private final PlacementTaskSyncService placementTaskSyncService;
    private final ObjectMapper objectMapper;
    private final SysTaskMapper taskMapper;
    private final SysTaskFileMapper taskFileMapper;

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
                .and(query.getRelatedUserId() != null, w -> w
                        .eq(GeoContentPlacement::getPublisherUserId, query.getRelatedUserId())
                        .or()
                        .eq(GeoContentPlacement::getOwnerUserId, query.getRelatedUserId()))
                .orderByDesc(GeoContentPlacement::getId);
        com.base.admin.util.QueryWrappers.applyCreateTimeRange(wrapper, query, GeoContentPlacement::getCreateTime);

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

    private static SysTaskFileVO toTaskFileVo(SysTaskFile f) {
        SysTaskFileVO vo = new SysTaskFileVO();
        vo.setId(f.getId());
        vo.setTaskId(f.getTaskId());
        vo.setBizType(f.getBizType());
        vo.setBizId(f.getBizId());
        vo.setFileName(f.getFileName());
        vo.setFileUrl(f.getFileUrl());
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
        placement.setTitle("");
        placement.setSource(Constants.CONTENT_SOURCE_MANUAL);
        placement.setSourcePlacementId(null);
        placement.setPlacementProgress(Constants.CONTENT_AGG_NONE);
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
        fillPlacement(placement, dto);
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
    }

    @Override
    @Transactional
    public List<GeoContentPlacementListVO> generateSimilar(Long placementId, String provider) {
        GeoContentPlacement source = requirePlacement(placementId);
        if (!StringUtils.hasText(source.getTargetQuestion())) {
            throw new BusinessException("当前记录缺少目标问题，无法生成相似问题");
        }
        SimilarQuestionsResult gen = generateSimilarQuestions(source, provider);
        List<String> questions = gen.questions();
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
            row.setSourceAiModel(gen.sourceAiModel());
            row.setPlacementProgress(Constants.CONTENT_AGG_NONE);
            row.setRemark("由「" + source.getTargetQuestion() + "」生成，待分配发布人/撰写人");
            placementMapper.insert(row);
            placementTaskSyncService.ensureTasksForPlacement(row);
            created.add(toListVo(row, List.of(), 0));
        }
        return created;
    }

    @Override
    public List<GeoAiProviderOptionVO> listAiProviders() {
        // 仅返回本地模板 + 系统管理中已配 Key 且启用的云厂商
        return sysAiProviderService.listReadyOptions();
    }

    private record SimilarQuestionsResult(List<String> questions, String sourceAiModel) {
    }

    private SimilarQuestionsResult generateSimilarQuestions(GeoContentPlacement source, String provider) {
        String topic = nz(source.getTopicName());
        String question = source.getTargetQuestion().trim();
        String p = StringUtils.hasText(provider) ? provider : null;
        // 显式选本地，或未选且默认也是本地
        boolean forceLocal = p != null && "local".equalsIgnoreCase(p.trim());
        boolean useAi = !forceLocal && (p != null ? aiChatService.isProviderConfigured(p) : aiChatService.isEnabled());
        if (useAi) {
            try {
                String system = """
                        你是 GEO 内容投放助手。请围绕给定目标问题，生成 3 到 8 条语义相近但表述不同的「待投放目标问题」。
                        要求：
                        1. 只输出 JSON 数组，例如 ["问题1","问题2"]，不要 markdown，不要解释；
                        2. 问题要适合中文内容投放，口语化、可检索；
                        3. 不要与原问题完全相同，也不要重复。
                        """;
                String user = "话题：" + (topic.isEmpty() ? "未指定" : topic) + "\n原目标问题：" + question;
                String content = aiChatService.chat(system, user, p);
                List<String> parsed = parseQuestionArray(content);
                if (!parsed.isEmpty()) {
                    int take = ThreadLocalRandom.current().nextInt(3, 9);
                    List<String> questions = parsed.stream().filter(q -> !q.equals(question)).distinct().limit(take).toList();
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
        return new SimilarQuestionsResult(heuristicSimilarQuestions(topic, question), "本地模板");
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

    private List<String> heuristicSimilarQuestions(String topic, String question) {
        String base = question.replaceAll("[？?！!。]$", "").trim();
        String topicPrefix = StringUtils.hasText(topic) ? topic : "相关场景";
        List<String> pool = new ArrayList<>(GeoSimilarQuestionTemplates.ALL);
        Collections.shuffle(pool, ThreadLocalRandom.current());
        int take = ThreadLocalRandom.current().nextInt(3, 9);
        List<String> picked = new ArrayList<>();
        for (String tpl : pool) {
            String q = tpl.replace("{base}", base).replace("{topic}", topicPrefix).trim();
            if (!StringUtils.hasText(q) || q.equals(question) || picked.contains(q)) {
                continue;
            }
            picked.add(q);
            if (picked.size() >= take) {
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
            List<CiteHeader> citeHeaders = readCiteHeaders(sheet);
            int remarkCol = citeHeaders.get(citeHeaders.size() - 1).col() + 1;
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
                    item.setPublishTime(parseFlexibleDate(publishTimeRaw));
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
                        String citeKey = askQuestion.trim() + "\0" + header.platform() + "\0" + url.trim();
                        if (!dedupeCites.add(citeKey)) {
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
        item.setContentForm(normalizeContentForm(dto.getContentForm(), dto.getPlatformName()));
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
        vo.setSourceAiModel(entity.getSourceAiModel());
        vo.setRemark(entity.getRemark());
        vo.setPlatformCount(items.size());
        vo.setSuccessCount(success);
        vo.setCiteCount(citeCount);
        vo.setProofFileCount(0);
        String progress = StringUtils.hasText(entity.getPlacementProgress())
                ? entity.getPlacementProgress()
                : aggregateStatus(items.stream().map(GeoContentPlacementItem::getPublishStatus).toList());
        vo.setAggregateStatus(progress);
        vo.setPublishProgress(publishProgress(items.size(), success));
        return vo;
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
                    && !item.getPublishTime().isBefore(weekStart)
                    && !item.getPublishTime().isAfter(weekEnd);
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
                && !item.getPublishTime().isBefore(start)
                && !item.getPublishTime().isAfter(end);
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
                .ge(GeoContentPlacementItem::getPublishTime, start)
                .le(GeoContentPlacementItem::getPublishTime, end)
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
                        || item.getPublishTime().isBefore(start)
                        || item.getPublishTime().isAfter(end)) {
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
            String date = j.item.getPublishTime() == null ? "-" : j.item.getPublishTime().toString();
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
            String date = j.item.getPublishTime() == null ? "-" : j.item.getPublishTime().toString();
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
            String date = c.item.getPublishTime() == null ? "-" : c.item.getPublishTime().toString();
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
            String date = j.item.getPublishTime() == null ? "-" : j.item.getPublishTime().toString();
            String base = date + "\0" + nz(j.placement.getTopicName()) + "\0"
                    + nz(j.placement.getPublisherName()) + "\0" + nz(j.item.getPlatformName());
            successByBase.computeIfAbsent(base, k -> new HashSet<>()).add(j.item.getId());
        }
        Map<String, Set<Long>> citedByAi = new HashMap<>();
        for (CiteJoin c : data.cites) {
            String date = c.item.getPublishTime() == null ? "-" : c.item.getPublishTime().toString();
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
        vo.setPublishTime(j.item.getPublishTime() == null ? null : j.item.getPublishTime().toString());
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
            LocalDate d = j.item.getPublishTime();
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
            LocalDate d = c.item.getPublishTime();
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
