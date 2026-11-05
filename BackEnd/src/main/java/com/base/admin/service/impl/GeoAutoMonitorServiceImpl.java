package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.base.admin.common.PageResult;
import com.base.admin.config.GeoAutoMonitorProperties;
import com.base.admin.domain.dto.GeoAutoBrowserLoginDTO;
import com.base.admin.domain.dto.GeoAutoProbeDTO;
import com.base.admin.domain.dto.GeoAutoQuestionDTO;
import com.base.admin.domain.dto.GeoAutoQuestionQueryDTO;
import com.base.admin.domain.dto.GeoAutoResultQueryDTO;
import com.base.admin.domain.dto.GeoAutoTaskDTO;
import com.base.admin.domain.dto.GeoAutoTaskQueryDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.GeoMonitorQuestion;
import com.base.admin.domain.entity.GeoMonitorResult;
import com.base.admin.domain.entity.GeoMonitorRun;
import com.base.admin.domain.entity.GeoMonitorTask;
import com.base.admin.domain.entity.GeoPlatform;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.vo.GeoAutoPlatformOptionVO;
import com.base.admin.domain.vo.GeoAutoQuestionOptionVO;
import com.base.admin.domain.vo.GeoAutoQuestionVO;
import com.base.admin.domain.vo.GeoAutoResultDetailVO;
import com.base.admin.domain.vo.GeoAutoResultMatrixVO;
import com.base.admin.domain.vo.GeoAutoResultVO;
import com.base.admin.domain.vo.GeoAutoSeedResultVO;
import com.base.admin.domain.vo.GeoAutoTaskVO;
import com.base.admin.domain.vo.GeoBrowserAccountVO;
import com.base.admin.domain.vo.GeoBrowserLoginVO;
import com.base.admin.domain.vo.GeoBrowserProbeVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.GeoMonitorDailyMapper;
import com.base.admin.mapper.GeoMonitorQuestionMapper;
import com.base.admin.mapper.GeoMonitorResultMapper;
import com.base.admin.mapper.GeoMonitorRunMapper;
import com.base.admin.mapper.GeoMonitorTaskMapper;
import com.base.admin.mapper.GeoPlatformMapper;
import com.base.admin.mapper.GeoTopicMapper;
import com.base.admin.service.GeoAutoMonitorExecutor;
import com.base.admin.service.GeoAutoMonitorService;
import com.base.admin.service.GeoBrowserAccountService;
import com.base.admin.service.GeoBrowserProbeService;
import com.base.admin.service.FileStorageService;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoAutoMonitorServiceImpl implements GeoAutoMonitorService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int QUESTION_OPTIONS_LIMIT = 500;
    private static final int MATRIX_ROW_LIMIT = 300;

    private final GeoMonitorQuestionMapper questionMapper;
    private final GeoMonitorTaskMapper taskMapper;
    private final GeoMonitorRunMapper runMapper;
    private final GeoMonitorResultMapper resultMapper;
    private final GeoMonitorDailyMapper dailyMapper;
    private final GeoContentPlacementMapper placementMapper;
    private final GeoPlatformMapper platformMapper;
    private final GeoTopicMapper topicMapper;
    private final GeoAutoMonitorExecutor executor;
    private final GeoBrowserProbeService probeService;
    private final GeoBrowserAccountService accountService;
    private final FileStorageService fileStorageService;
    private final GeoAutoMonitorProperties properties;
    private final ObjectMapper objectMapper;

    // ==================== 监控问题库 ====================

    @Override
    public PageResult<GeoAutoQuestionVO> listQuestions(GeoAutoQuestionQueryDTO query) {
        LambdaQueryWrapper<GeoMonitorQuestion> wrapper = new LambdaQueryWrapper<GeoMonitorQuestion>()
                .like(StringUtils.hasText(query.getQuestionText()), GeoMonitorQuestion::getQuestionText, query.getQuestionText())
                .eq(query.getTopicId() != null, GeoMonitorQuestion::getTopicId, query.getTopicId())
                .eq(query.getEnabled() != null, GeoMonitorQuestion::getEnabled, query.getEnabled())
                .eq(StringUtils.hasText(query.getFreq()), GeoMonitorQuestion::getFreq, query.getFreq())
                .orderByAsc(GeoMonitorQuestion::getSortOrder)
                .orderByDesc(GeoMonitorQuestion::getId);
        com.base.admin.util.QueryWrappers.applyCreateTimeRange(wrapper, query, GeoMonitorQuestion::getCreateTime);
        Page<GeoMonitorQuestion> page = questionMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, String> topicNames = topicNames(page.getRecords().stream().map(GeoMonitorQuestion::getTopicId).toList());
        List<GeoAutoQuestionVO> rows = page.getRecords().stream()
                .map(q -> toQuestionVO(q, topicNames))
                .toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public List<GeoAutoQuestionOptionVO> questionOptions(Long topicId) {
        List<GeoMonitorQuestion> list = questionMapper.selectList(new LambdaQueryWrapper<GeoMonitorQuestion>()
                .eq(GeoMonitorQuestion::getEnabled, 1)
                .eq(topicId != null, GeoMonitorQuestion::getTopicId, topicId)
                .orderByAsc(GeoMonitorQuestion::getSortOrder)
                .orderByAsc(GeoMonitorQuestion::getId)
                .last("LIMIT " + QUESTION_OPTIONS_LIMIT));
        Map<Long, String> topicNames = topicNames(list.stream().map(GeoMonitorQuestion::getTopicId).toList());
        return list.stream().map(q -> {
            GeoAutoQuestionOptionVO vo = new GeoAutoQuestionOptionVO();
            vo.setId(q.getId());
            vo.setQuestionText(q.getQuestionText());
            vo.setTopicId(q.getTopicId());
            vo.setTopicName(topicNames.getOrDefault(q.getTopicId(), ""));
            vo.setFreq(q.getFreq());
            vo.setBrandWords(q.getBrandWords());
            vo.setCompetitorWords(q.getCompetitorWords());
            return vo;
        }).toList();
    }

    @Override
    public void createQuestion(GeoAutoQuestionDTO dto) {
        String text = dto.getQuestionText().trim();
        assertQuestionTextFree(text, null);
        assertTopicExists(dto.getTopicId());
        GeoMonitorQuestion question = new GeoMonitorQuestion();
        applyQuestionFields(question, dto, text);
        questionMapper.insert(question);
    }

    @Override
    public void updateQuestion(GeoAutoQuestionDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("问题ID不能为空");
        }
        GeoMonitorQuestion question = questionMapper.selectById(dto.getId());
        if (question == null) {
            throw new BusinessException("监控问题不存在");
        }
        String text = dto.getQuestionText().trim();
        assertQuestionTextFree(text, dto.getId());
        assertTopicExists(dto.getTopicId());
        applyQuestionFields(question, dto, text);
        questionMapper.updateById(question);
    }

    @Override
    public void deleteQuestion(Long id) {
        long used = resultMapper.selectCount(new LambdaQueryWrapper<GeoMonitorResult>().eq(GeoMonitorResult::getQuestionId, id));
        if (used > 0) {
            throw new BusinessException("该问题已有自动监测结果，停用即可，不建议删除（保留历史可追溯）");
        }
        questionMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteQuestionBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的问题");
        }
        for (Long id : ids) {
            if (id != null) {
                deleteQuestion(id);
            }
        }
    }

    @Override
    public void setQuestionsEnabled(List<Long> ids, Integer enabled) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择问题");
        }
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new BusinessException("enabled 取值仅 0/1");
        }
        questionMapper.update(null, new LambdaUpdateWrapper<GeoMonitorQuestion>()
                .in(GeoMonitorQuestion::getId, ids)
                .set(GeoMonitorQuestion::getEnabled, enabled));
    }

    @Override
    public GeoAutoSeedResultVO seedQuestions() {
        // 来源1：日监测关键字（term_type 周巡查 → 每周）
        Map<String, GeoMonitorQuestion> candidates = new LinkedHashMap<>();
        List<GeoMonitorDaily> dailies = dailyMapper.selectList(new LambdaQueryWrapper<GeoMonitorDaily>()
                .select(GeoMonitorDaily::getKeyword, GeoMonitorDaily::getTopicId, GeoMonitorDaily::getTermType)
                .isNotNull(GeoMonitorDaily::getKeyword)
                .ne(GeoMonitorDaily::getKeyword, ""));
        for (GeoMonitorDaily daily : dailies) {
            candidates.computeIfAbsent(daily.getKeyword().trim(), k -> {
                GeoMonitorQuestion q = new GeoMonitorQuestion();
                q.setQuestionText(k);
                q.setTopicId(daily.getTopicId() != null ? daily.getTopicId() : 0L);
                q.setFreq("周巡查".equals(daily.getTermType()) ? GeoMonitorTask.FREQ_WEEKLY : GeoMonitorTask.FREQ_DAILY);
                return q;
            });
        }
        // 来源2：内容投放目标问题（默认每日）
        List<GeoContentPlacement> placements = placementMapper.selectList(new LambdaQueryWrapper<GeoContentPlacement>()
                .select(GeoContentPlacement::getTargetQuestion, GeoContentPlacement::getTopicId)
                .isNotNull(GeoContentPlacement::getTargetQuestion)
                .ne(GeoContentPlacement::getTargetQuestion, ""));
        for (GeoContentPlacement placement : placements) {
            candidates.computeIfAbsent(placement.getTargetQuestion().trim(), k -> {
                GeoMonitorQuestion q = new GeoMonitorQuestion();
                q.setQuestionText(k);
                q.setTopicId(placement.getTopicId() != null ? placement.getTopicId() : 0L);
                q.setFreq(GeoMonitorTask.FREQ_DAILY);
                return q;
            });
        }

        Set<String> existing = questionMapper.selectList(new LambdaQueryWrapper<GeoMonitorQuestion>()
                        .select(GeoMonitorQuestion::getQuestionText))
                .stream().map(GeoMonitorQuestion::getQuestionText).collect(Collectors.toSet());

        GeoAutoSeedResultVO vo = new GeoAutoSeedResultVO();
        vo.setScanned(candidates.size());
        int imported = 0;
        for (GeoMonitorQuestion candidate : candidates.values()) {
            if (existing.contains(candidate.getQuestionText())) {
                continue;
            }
            candidate.setBrandWords("");
            candidate.setCompetitorWords("");
            candidate.setOwnerUserId(0L);
            candidate.setOwnerName("");
            candidate.setEnabled(1);
            candidate.setSortOrder(0);
            questionMapper.insert(candidate);
            imported++;
        }
        vo.setImported(imported);
        vo.setSkipped(candidates.size() - imported);
        log.info("问题库洗入完成：扫描 {}，导入 {}，跳过 {}", vo.getScanned(), vo.getImported(), vo.getSkipped());
        return vo;
    }

    // ==================== 监测任务 ====================

    @Override
    public PageResult<GeoAutoTaskVO> listTasks(GeoAutoTaskQueryDTO query) {
        LambdaQueryWrapper<GeoMonitorTask> wrapper = new LambdaQueryWrapper<GeoMonitorTask>()
                .like(StringUtils.hasText(query.getTaskName()), GeoMonitorTask::getTaskName, query.getTaskName())
                .eq(query.getEnabled() != null, GeoMonitorTask::getEnabled, query.getEnabled())
                .orderByAsc(GeoMonitorTask::getSortOrder)
                .orderByDesc(GeoMonitorTask::getId);
        com.base.admin.util.QueryWrappers.applyCreateTimeRange(wrapper, query, GeoMonitorTask::getCreateTime);
        Page<GeoMonitorTask> page = taskMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<GeoAutoTaskVO> rows = page.getRecords().stream().map(this::toTaskVO).toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public List<GeoAutoPlatformOptionVO> platformOptions() {
        List<GeoPlatform> platforms = platformMapper.selectList(new LambdaQueryWrapper<GeoPlatform>()
                .eq(GeoPlatform::getPlatformType, "AI平台")
                .orderByAsc(GeoPlatform::getSortOrder));
        Map<String, com.base.admin.config.GeoBrowserProbeProperties.PlatformProfile> profiles = probeService.profiles();
        return platforms.stream().map(p -> {
            GeoAutoPlatformOptionVO vo = new GeoAutoPlatformOptionVO();
            vo.setId(p.getId());
            vo.setPlatformName(p.getPlatformName());
            com.base.admin.config.GeoBrowserProbeProperties.PlatformProfile profile = profiles.get(p.getPlatformName());
            boolean ready = profile != null && profile.isEnabled()
                    && StringUtils.hasText(profile.getUrl());
            vo.setProviderCode(ready ? "BROWSER" : "");
            vo.setProviderReady(ready);
            if (!ready) {
                vo.setHint("未配置网页版采集适配器，请在 geo.auto-monitor.browser.profiles 中补充");
            } else if (!probeService.hasStorageState(p.getPlatformName())) {
                vo.setHint("未配置登录态（未登录时可能无法提问或生成分享链接）");
            }
            return vo;
        }).toList();
    }

    @Override
    public void createTask(GeoAutoTaskDTO dto) {
        GeoMonitorTask task = new GeoMonitorTask();
        applyTaskFields(task, dto);
        assertTaskRunnable(task);
        taskMapper.insert(task);
        executor.scheduleNextRun(task.getId());
    }

    @Override
    public void updateTask(GeoAutoTaskDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("任务ID不能为空");
        }
        GeoMonitorTask task = taskMapper.selectById(dto.getId());
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        Long lastRunId = task.getLastRunId();
        applyTaskFields(task, dto);
        task.setLastRunId(lastRunId);
        assertTaskRunnable(task);
        taskMapper.updateById(task);
        executor.scheduleNextRun(task.getId());
    }

    @Override
    public void deleteTask(Long id) {
        if (executor.isRunning(id)) {
            throw new BusinessException("任务正在运行中，无法删除");
        }
        taskMapper.deleteById(id);
    }

    @Override
    public void toggleTask(Long id, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new BusinessException("enabled 取值仅 0/1");
        }
        GeoMonitorTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        task.setEnabled(enabled);
        if (enabled == 1) {
            task.setNextRunTime(GeoAutoMonitorExecutor.computeNextRunTime(task, LocalDateTime.now()));
        } else {
            task.setNextRunTime(null);
        }
        taskMapper.updateById(task);
    }

    @Override
    public GeoMonitorRun startTaskRun(Long taskId) {
        return executor.startRun(taskId, GeoMonitorRun.TRIGGER_MANUAL);
    }

    // ==================== 运行批次与结果 ====================

    @Override
    public PageResult<GeoMonitorRun> listRuns(Long taskId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<GeoMonitorRun> wrapper = new LambdaQueryWrapper<GeoMonitorRun>()
                .eq(taskId != null, GeoMonitorRun::getTaskId, taskId)
                .orderByDesc(GeoMonitorRun::getId);
        Page<GeoMonitorRun> page = runMapper.selectPage(new Page<>(
                pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    public GeoMonitorRun getRun(Long id) {
        GeoMonitorRun run = runMapper.selectById(id);
        if (run == null) {
            throw new BusinessException("运行批次不存在");
        }
        return run;
    }

    @Override
    public PageResult<GeoAutoResultVO> listResults(GeoAutoResultQueryDTO query) {
        LambdaQueryWrapper<GeoMonitorResult> wrapper = resultWrapper(query);
        Page<GeoMonitorResult> page = resultMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<GeoMonitorResult> records = page.getRecords();
        Map<Long, String> topicNames = topicNames(records.stream().map(GeoMonitorResult::getTopicId).toList());
        Map<Long, GeoMonitorRun> runs = runsById(records.stream().map(GeoMonitorResult::getRunId).toList());
        List<GeoAutoResultVO> rows = records.stream()
                .map(r -> toResultVO(r, topicNames, runs.get(r.getRunId())))
                .toList();
        return new PageResult<>(page.getTotal(), rows);
    }

    @Override
    public GeoAutoResultDetailVO getResultDetail(Long id) {
        GeoMonitorResult result = resultMapper.selectById(id);
        if (result == null) {
            throw new BusinessException("监测结果不存在");
        }
        Map<Long, String> topicNames = topicNames(List.of(result.getTopicId()));
        GeoMonitorRun run = runMapper.selectById(result.getRunId());

        GeoAutoResultDetailVO vo = new GeoAutoResultDetailVO();
        copyResultVO(vo, result, topicNames.getOrDefault(result.getTopicId(), ""), run);
        vo.setAnswerText(result.getAnswerText());
        vo.setReasoningText(result.getReasoningText());
        vo.setProbeUrl(result.getProbeUrl());
        vo.setProbeUrlSource(parseDiagnosticField(result.getProbeRequest(), "probeUrlSource"));
        vo.setProbeRequest(result.getProbeRequest());
        vo.setScreenshotUrl(result.getScreenshotUrl());
        vo.setScreenshotType(result.getScreenshotType());
        vo.setJudgeRaw(result.getJudgeRaw());
        vo.setCitations(parseCitations(result.getCitations()));
        vo.setDiffs(buildDiffs(result));
        return vo;
    }

    /**
     * 单题浏览器采集试跑：不落业务库，返回回答/思考过程/分享链接/截图/诊断，
     * 用于上线前核对各平台选择器与登录态（对标模力指数问答明细的取证内容）。
     */
    @Override
    public GeoBrowserProbeVO probeOnce(GeoAutoProbeDTO dto) {
        boolean deepThink = Boolean.TRUE.equals(dto.getDeepThink());
        boolean screenshot = !Boolean.FALSE.equals(dto.getScreenshot());
        GeoBrowserProbeService.ProbeOutcome outcome = probeService.probe(
                dto.getPlatform().trim(), dto.getQuestion().trim(), deepThink, screenshot,
                dto.getRegion(), null);

        GeoBrowserProbeVO vo = new GeoBrowserProbeVO();
        vo.setSuccess(outcome.success());
        vo.setError(outcome.error());
        vo.setPlatform(outcome.platform());
        vo.setQuestion(outcome.question());
        vo.setAnswerText(outcome.answerText());
        vo.setReasoningText(outcome.reasoningText());
        vo.setProbeUrl(outcome.probeUrl());
        vo.setProbeUrlSource(outcome.probeUrlSource());
        vo.setScreenshotUrl(outcome.screenshotUrl());
        vo.setScreenshotType(outcome.screenshotType());
        vo.setPageUrl(outcome.pageUrl());
        vo.setModelLabel(outcome.modelLabel());
        vo.setDeepThinkApplied(outcome.deepThinkApplied());
        vo.setDiagnostic(outcome.diagnostic());
        vo.setCitations(outcome.citations().stream().map(c -> {
            GeoAutoResultDetailVO.Citation item = new GeoAutoResultDetailVO.Citation();
            item.setTitle(c.title());
            item.setSite(c.site());
            item.setUrl(c.url());
            item.setContext(c.context());
            return item;
        }).toList());
        return vo;
    }

    @Override
    public GeoBrowserLoginVO browserLogin(GeoAutoBrowserLoginDTO dto) {
        GeoBrowserProbeService.LoginOutcome outcome = probeService.ensureLogin(
                dto.getPlatform().trim(), Boolean.TRUE.equals(dto.getForceRefresh()), dto.getAccountId());
        GeoBrowserLoginVO vo = new GeoBrowserLoginVO();
        vo.setSuccess(outcome.success());
        vo.setError(outcome.error());
        vo.setPlatform(outcome.platform());
        vo.setAccountLabel(outcome.accountLabel());
        vo.setSource(outcome.source());
        vo.setStatePath(outcome.statePath() == null ? "" : outcome.statePath().toString());
        vo.setScreenshotUrl(outcome.screenshotUrl());
        vo.setDiagnostic(outcome.diagnostic());
        return vo;
    }

    @Override
    public List<GeoBrowserAccountVO> browserAccountStatus() {
        return accountService.statusList();
    }

    /** 人工补录真实 C 端截图（浏览器通道不可用或需要留档客户交付件时） */
    @Override
    public String saveResultScreenshot(Long id, MultipartFile file) {
        GeoMonitorResult result = resultMapper.selectById(id);
        if (result == null) {
            throw new BusinessException("监测结果不存在");
        }
        String stored = fileStorageService.saveGeoImage(file);
        result.setScreenshotUrl(stored);
        result.setScreenshotType("MANUAL");
        resultMapper.updateById(result);
        return stored;
    }

    /** 上传平台登录态 storageState（Playwright 导出的 JSON），供后续采集复用 */
    @Override
    public String saveBrowserState(String platform, MultipartFile file) {
        if (!StringUtils.hasText(platform)) {
            throw new BusinessException("平台名不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择 storageState JSON 文件");
        }
        try {
            byte[] bytes = file.getBytes();
            objectMapper.readTree(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            probeService.saveStorageState(platform.trim(), bytes);
            return platform.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("登录态文件保存失败：" + e.getMessage());
        }
    }

    @Override
    public GeoAutoResultMatrixVO resultMatrix(GeoAutoResultQueryDTO query) {
        LocalDate end = query.getDateEnd() != null ? query.getDateEnd() : LocalDate.now();
        LocalDate start = query.getDateStart() != null ? query.getDateStart() : end.minusDays(6);
        LambdaQueryWrapper<GeoMonitorResult> wrapper = new LambdaQueryWrapper<GeoMonitorResult>()
                .select(GeoMonitorResult.class, f -> !"answer_text".equals(f.getColumn()) && !"judge_raw".equals(f.getColumn()))
                .ge(GeoMonitorResult::getCreateTime, start.atStartOfDay())
                .le(GeoMonitorResult::getCreateTime, end.atTime(LocalTime.of(23, 59, 59)))
                .eq(StringUtils.hasText(query.getPlatform()), GeoMonitorResult::getPlatform, query.getPlatform())
                .eq(query.getTopicId() != null, GeoMonitorResult::getTopicId, query.getTopicId())
                .like(StringUtils.hasText(query.getQuestionText()), GeoMonitorResult::getQuestionText, query.getQuestionText())
                .in(GeoMonitorResult::getStatus, List.of(GeoMonitorResult.STATUS_SUCCESS, GeoMonitorResult.STATUS_SKIPPED_MANUAL))
                .orderByDesc(GeoMonitorResult::getId);
        List<GeoMonitorResult> results = resultMapper.selectList(wrapper);

        Map<Long, String> topicNames = topicNames(results.stream().map(GeoMonitorResult::getTopicId).toList());
        Map<String, Integer> platformOrder = platformOrderIndex();

        // questionId -> row；platform -> cell（结果按 id 倒序，首个即最新）
        Map<Long, GeoAutoResultMatrixVO.Row> rowMap = new LinkedHashMap<>();
        Set<String> usedPlatforms = new HashSet<>();
        for (GeoMonitorResult r : results) {
            usedPlatforms.add(r.getPlatform());
            GeoAutoResultMatrixVO.Row row = rowMap.computeIfAbsent(r.getQuestionId(), qid -> {
                GeoAutoResultMatrixVO.Row nr = new GeoAutoResultMatrixVO.Row();
                nr.setQuestionId(qid);
                nr.setQuestionText(r.getQuestionText());
                nr.setTopicId(r.getTopicId());
                nr.setTopicName(topicNames.getOrDefault(r.getTopicId(), ""));
                nr.setCells(new LinkedHashMap<>());
                return nr;
            });
            row.getCells().computeIfAbsent(r.getPlatform(), p -> {
                GeoAutoResultMatrixVO.Cell cell = new GeoAutoResultMatrixVO.Cell();
                cell.setResultId(r.getId());
                cell.setRunDate(r.getCreateTime() == null ? "" : r.getCreateTime().toLocalDate().toString());
                cell.setMentioned(r.getMentioned());
                cell.setRankNo(r.getRankNo());
                cell.setRecommendStatus(r.getRecommendStatus());
                cell.setSentiment(r.getSentiment());
                cell.setStatus(r.getStatus());
                return cell;
            });
            if (rowMap.size() > MATRIX_ROW_LIMIT) {
                break;
            }
        }

        GeoAutoResultMatrixVO vo = new GeoAutoResultMatrixVO();
        vo.setPlatforms(usedPlatforms.stream()
                .sorted(Comparator.<String>comparingInt(p -> platformOrder.getOrDefault(p, Integer.MAX_VALUE))
                        .thenComparing(Comparator.naturalOrder()))
                .toList());
        List<GeoAutoResultMatrixVO.Row> rows = new ArrayList<>(rowMap.values());
        for (GeoAutoResultMatrixVO.Row row : rows) {
            long mentioned = row.getCells().values().stream().filter(c -> c.getMentioned() != null && c.getMentioned() == 1).count();
            row.setMentionSummary(mentioned + "/" + row.getCells().size());
        }
        vo.setRows(rows);
        return vo;
    }

    // ==================== 私有辅助 ====================

    private void applyQuestionFields(GeoMonitorQuestion question, GeoAutoQuestionDTO dto, String text) {
        question.setQuestionText(text);
        question.setTopicId(dto.getTopicId());
        question.setBrandWords(dto.getBrandWords() == null ? "" : dto.getBrandWords().trim());
        question.setCompetitorWords(dto.getCompetitorWords() == null ? "" : dto.getCompetitorWords().trim());
        question.setFreq(GeoMonitorTask.FREQ_WEEKLY.equals(dto.getFreq())
                ? GeoMonitorTask.FREQ_WEEKLY : GeoMonitorTask.FREQ_DAILY);
        question.setOwnerUserId(dto.getOwnerUserId() != null ? dto.getOwnerUserId() : 0L);
        question.setOwnerName(dto.getOwnerName() == null ? "" : dto.getOwnerName().trim());
        question.setEnabled(dto.getEnabled() == null || dto.getEnabled() == 1 ? 1 : 0);
        question.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        question.setRemark(dto.getRemark() == null ? "" : dto.getRemark());
    }

    private void assertQuestionTextFree(String text, Long excludeId) {
        Long count = questionMapper.selectCount(new LambdaQueryWrapper<GeoMonitorQuestion>()
                .eq(GeoMonitorQuestion::getQuestionText, text)
                .ne(excludeId != null, GeoMonitorQuestion::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("该监控问题已存在");
        }
    }

    private void assertTopicExists(Long topicId) {
        if (topicId == null || topicMapper.selectById(topicId) == null) {
            throw new BusinessException("所属话题不存在，请先到话题管理创建");
        }
    }

    private void applyTaskFields(GeoMonitorTask task, GeoAutoTaskDTO dto) {
        task.setTaskName(dto.getTaskName().trim());
        String scopeType = GeoMonitorTask.SCOPE_QUESTION.equals(dto.getScopeType())
                ? GeoMonitorTask.SCOPE_QUESTION : GeoMonitorTask.SCOPE_TOPIC;
        task.setScopeType(scopeType);
        if (dto.getScopeIds() == null || dto.getScopeIds().isEmpty()) {
            throw new BusinessException("请至少圈选一个话题或问题");
        }
        task.setScopeIds(dto.getScopeIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        if (dto.getPlatforms() == null || dto.getPlatforms().isEmpty()) {
            throw new BusinessException("请至少选择一个监测平台");
        }
        List<String> platformNames = dto.getPlatforms().stream().map(String::trim).filter(StringUtils::hasText).toList();
        if (platformNames.isEmpty()) {
            throw new BusinessException("请至少选择一个监测平台");
        }
        assertPlatformsAreAi(platformNames);
        task.setPlatforms(String.join(",", platformNames));
        String freq = GeoMonitorTask.FREQ_WEEKLY.equals(dto.getFreq())
                ? GeoMonitorTask.FREQ_WEEKLY : GeoMonitorTask.FREQ_DAILY;
        task.setFreq(freq);
        task.setRunTime(dto.getRunTime() != null ? dto.getRunTime() : LocalTime.of(8, 30));
        if (GeoMonitorTask.FREQ_WEEKLY.equals(freq)) {
            task.setWeekDay(dto.getWeekDay() == null ? 1 : Math.min(7, Math.max(1, dto.getWeekDay())));
        } else {
            task.setWeekDay(null);
        }
        task.setOverwriteManual(dto.getOverwriteManual() != null && dto.getOverwriteManual() == 1 ? 1 : 0);
        task.setDeepThink(dto.getDeepThink() != null && dto.getDeepThink() == 1 ? 1 : 0);
        String shotMode = dto.getScreenshotMode();
        task.setScreenshotMode(GeoMonitorTask.SHOT_DISABLED.equals(shotMode)
                || GeoMonitorTask.SHOT_ALL.equals(shotMode) ? shotMode : GeoMonitorTask.SHOT_MENTION);
        task.setProbeRegion(StringUtils.hasText(dto.getProbeRegion())
                ? dto.getProbeRegion().trim() : "全国随机");
        task.setNotifyEnabled(dto.getNotifyEnabled() == null || dto.getNotifyEnabled() == 1 ? 1 : 0);
        task.setEnabled(dto.getEnabled() == null || dto.getEnabled() == 1 ? 1 : 0);
        task.setSortOrder(0);
        task.setRemark(dto.getRemark() == null ? "" : dto.getRemark());
    }

    private void assertPlatformsAreAi(List<String> platformNames) {
        List<GeoPlatform> platforms = platformMapper.selectList(new LambdaQueryWrapper<GeoPlatform>()
                .in(GeoPlatform::getPlatformName, platformNames));
        List<String> invalid = new ArrayList<>();
        for (GeoPlatform platform : platforms) {
            if (!"AI平台".equals(platform.getPlatformType())) {
                invalid.add(platform.getPlatformName() + "（非AI平台）");
            }
        }
        List<String> missing = platformNames.stream()
                .filter(n -> platforms.stream().noneMatch(p -> p.getPlatformName().equals(n)))
                .toList();
        if (!missing.isEmpty()) {
            invalid.addAll(missing.stream().map(n -> n + "（不存在）").toList());
        }
        if (!invalid.isEmpty()) {
            throw new BusinessException("监测平台无效：" + String.join("、", invalid));
        }
    }

    private void assertTaskRunnable(GeoMonitorTask task) {
        if (executor.resolveQuestions(task).isEmpty()) {
            throw new BusinessException("任务圈选范围内没有启用的问题");
        }
    }

    private GeoAutoQuestionVO toQuestionVO(GeoMonitorQuestion question, Map<Long, String> topicNames) {
        GeoAutoQuestionVO vo = new GeoAutoQuestionVO();
        vo.setId(question.getId());
        vo.setQuestionText(question.getQuestionText());
        vo.setTopicId(question.getTopicId());
        vo.setTopicName(topicNames.getOrDefault(question.getTopicId(), ""));
        vo.setBrandWords(question.getBrandWords());
        vo.setCompetitorWords(question.getCompetitorWords());
        vo.setFreq(question.getFreq());
        vo.setOwnerUserId(question.getOwnerUserId());
        vo.setOwnerName(question.getOwnerName());
        vo.setEnabled(question.getEnabled());
        vo.setSortOrder(question.getSortOrder());
        vo.setRemark(question.getRemark());
        vo.setCreateTime(question.getCreateTime());
        return vo;
    }

    private GeoAutoTaskVO toTaskVO(GeoMonitorTask task) {
        GeoAutoTaskVO vo = new GeoAutoTaskVO();
        vo.setId(task.getId());
        vo.setTaskName(task.getTaskName());
        vo.setScopeType(task.getScopeType());
        vo.setScopeIds(task.getScopeIds());
        vo.setPlatforms(task.getPlatforms());
        vo.setPlatformList(splitCsv(task.getPlatforms()));
        vo.setScopeIdList(splitCsv(task.getScopeIds()).stream().map(Long::valueOf).toList());
        vo.setFreq(task.getFreq());
        vo.setRunTime(task.getRunTime());
        vo.setWeekDay(task.getWeekDay());
        vo.setOverwriteManual(task.getOverwriteManual());
        vo.setDeepThink(task.getDeepThink());
        vo.setScreenshotMode(task.getScreenshotMode());
        vo.setProbeRegion(task.getProbeRegion());
        vo.setNotifyEnabled(task.getNotifyEnabled());
        vo.setEnabled(task.getEnabled());
        vo.setLastRunId(task.getLastRunId());
        vo.setNextRunTime(task.getNextRunTime());
        vo.setSortOrder(task.getSortOrder());
        vo.setRemark(task.getRemark());
        vo.setCreateTime(task.getCreateTime());

        List<GeoMonitorQuestion> questions = executor.resolveQuestions(task);
        int questionCount = questions.size();
        vo.setQuestionCount(questionCount);
        int platformCount = splitCsv(task.getPlatforms()).size();
        int freqFactor = GeoMonitorTask.FREQ_WEEKLY.equals(task.getFreq()) ? 1 : 30;
        vo.setEstimatedDailyCalls(Math.round((float) (questionCount * platformCount * freqFactor) / 30));

        if (task.getLastRunId() != null) {
            GeoMonitorRun run = runMapper.selectById(task.getLastRunId());
            if (run != null) {
                vo.setLastRunStatus(run.getStatus());
                vo.setLastRunStartTime(run.getStartTime() == null ? "" : DATE_TIME.format(run.getStartTime()));
                vo.setLastRunProgress(run.getDoneCount() + "/" + run.getTotalCount());
            }
        }
        return vo;
    }

    private LambdaQueryWrapper<GeoMonitorResult> resultWrapper(GeoAutoResultQueryDTO query) {
        LocalDate end = query.getDateEnd() != null ? query.getDateEnd() : LocalDate.now();
        LocalDate start = query.getDateStart() != null ? query.getDateStart() : end.minusDays(6);
        return new LambdaQueryWrapper<GeoMonitorResult>()
                .select(GeoMonitorResult.class, f -> !"answer_text".equals(f.getColumn()) && !"judge_raw".equals(f.getColumn()))
                .ge(GeoMonitorResult::getCreateTime, start.atStartOfDay())
                .le(GeoMonitorResult::getCreateTime, end.atTime(LocalTime.of(23, 59, 59)))
                .eq(StringUtils.hasText(query.getPlatform()), GeoMonitorResult::getPlatform, query.getPlatform())
                .eq(query.getTopicId() != null, GeoMonitorResult::getTopicId, query.getTopicId())
                .like(StringUtils.hasText(query.getQuestionText()), GeoMonitorResult::getQuestionText, query.getQuestionText())
                .eq(query.getMentioned() != null, GeoMonitorResult::getMentioned, query.getMentioned())
                .eq(StringUtils.hasText(query.getSentiment()), GeoMonitorResult::getSentiment, query.getSentiment())
                .eq(StringUtils.hasText(query.getStatus()), GeoMonitorResult::getStatus, query.getStatus())
                .eq(query.getRunId() != null, GeoMonitorResult::getRunId, query.getRunId())
                .orderByDesc(GeoMonitorResult::getId);
    }

    private GeoAutoResultVO toResultVO(GeoMonitorResult result, Map<Long, String> topicNames, GeoMonitorRun run) {
        GeoAutoResultVO vo = new GeoAutoResultVO();
        copyResultVO(vo, result, topicNames.getOrDefault(result.getTopicId(), ""), run);
        return vo;
    }

    private void copyResultVO(GeoAutoResultVO vo, GeoMonitorResult result, String topicName, GeoMonitorRun run) {
        vo.setId(result.getId());
        vo.setRunId(result.getRunId());
        vo.setQuestionId(result.getQuestionId());
        vo.setQuestionText(result.getQuestionText());
        vo.setTopicId(result.getTopicId());
        vo.setTopicName(topicName);
        vo.setPlatform(result.getPlatform());
        vo.setChannel(result.getChannel());
        vo.setAnswerModel(result.getAnswerModel());
        vo.setDeepThink(result.getDeepThink());
        vo.setHasScreenshot(StringUtils.hasText(result.getScreenshotUrl()));
        vo.setRunDate(run != null ? run.getRunDate() : null);
        vo.setMentioned(result.getMentioned());
        vo.setRankNo(result.getRankNo());
        vo.setRecommendStatus(result.getRecommendStatus());
        vo.setSentiment(result.getSentiment());
        vo.setMentionPosition(result.getMentionPosition());
        vo.setNegativeContent(result.getNegativeContent());
        vo.setCompetitors(result.getCompetitors());
        vo.setStatus(result.getStatus());
        vo.setErrorMsg(result.getErrorMsg());
        vo.setLatencyMs(result.getLatencyMs());
        vo.setDailyId(result.getDailyId());
        vo.setCreateTime(result.getCreateTime() == null ? "" : DATE_TIME.format(result.getCreateTime()));
    }

    /** 与上一次同问题同平台成功结果对比（提及/排名/情感/推荐状态/竞品） */
    private List<GeoAutoResultDetailVO.DiffItem> buildDiffs(GeoMonitorResult current) {
        GeoMonitorResult prev = resultMapper.selectOne(new LambdaQueryWrapper<GeoMonitorResult>()
                .eq(GeoMonitorResult::getQuestionId, current.getQuestionId())
                .eq(GeoMonitorResult::getPlatform, current.getPlatform())
                .lt(GeoMonitorResult::getId, current.getId())
                .in(GeoMonitorResult::getStatus, List.of(GeoMonitorResult.STATUS_SUCCESS, GeoMonitorResult.STATUS_SKIPPED_MANUAL))
                .orderByDesc(GeoMonitorResult::getId)
                .last("LIMIT 1"));
        List<GeoAutoResultDetailVO.DiffItem> diffs = new ArrayList<>();
        if (prev == null) {
            return diffs;
        }
        int prevMentioned = prev.getMentioned() != null && prev.getMentioned() == 1 ? 1 : 0;
        int curMentioned = current.getMentioned() != null && current.getMentioned() == 1 ? 1 : 0;
        if (prevMentioned != curMentioned) {
            diffs.add(diff("mentioned", "是否提及",
                    prevMentioned == 1 ? "提及" : "未提及", curMentioned == 1 ? "提及" : "未提及",
                    curMentioned == 1 ? "up" : "down"));
        }
        if (!Objects.equals(prev.getRankNo(), current.getRankNo())) {
            String before = prev.getRankNo() == null ? "未上榜" : "第" + prev.getRankNo() + "位";
            String after = current.getRankNo() == null ? "未上榜" : "第" + current.getRankNo() + "位";
            String direction = "flat";
            if (prev.getRankNo() == null) {
                direction = "up";
            } else if (current.getRankNo() == null) {
                direction = "down";
            } else {
                direction = current.getRankNo() < prev.getRankNo() ? "up" : "down";
            }
            diffs.add(diff("rankNo", "排名", before, after, direction));
        }
        if (StringUtils.hasText(prev.getSentiment()) && !Objects.equals(prev.getSentiment(), current.getSentiment())) {
            diffs.add(diff("sentiment", "情感", prev.getSentiment(), current.getSentiment(),
                    sentimentScore(current.getSentiment()) > sentimentScore(prev.getSentiment()) ? "up" : "down"));
        }
        if (!Objects.equals(prev.getRecommendStatus(), current.getRecommendStatus())) {
            diffs.add(diff("recommendStatus", "推荐状态", nz(prev.getRecommendStatus()), nz(current.getRecommendStatus()), "flat"));
        }
        Set<String> prevCompetitors = splitToSet(prev.getCompetitors());
        Set<String> newCompetitors = splitToSet(current.getCompetitors()).stream()
                .filter(c -> !prevCompetitors.contains(c)).collect(Collectors.toSet());
        if (!newCompetitors.isEmpty()) {
            diffs.add(diff("competitors", "新增竞品", "—", String.join("、", newCompetitors), "down"));
        }
        return diffs;
    }

    private GeoAutoResultDetailVO.DiffItem diff(String field, String label, String before, String after, String direction) {
        GeoAutoResultDetailVO.DiffItem item = new GeoAutoResultDetailVO.DiffItem();
        item.setField(field);
        item.setLabel(label);
        item.setBefore(before);
        item.setAfter(after);
        item.setDirection(direction);
        return item;
    }

    private int sentimentScore(String sentiment) {
        return "正面".equals(sentiment) ? 2 : ("中性".equals(sentiment) ? 1 : 0);
    }

    /** 从采集诊断 JSON 里取单个字段（如 probeUrlSource），诊断缺失时返回空串 */
    private String parseDiagnosticField(String diagnosticJson, String field) {
        if (!StringUtils.hasText(diagnosticJson)) {
            return "";
        }
        try {
            return objectMapper.readTree(diagnosticJson).path(field).asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private List<GeoAutoResultDetailVO.Citation> parseCitations(String citationsJson) {
        if (!StringUtils.hasText(citationsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citationsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, GeoAutoResultDetailVO.Citation.class));
        } catch (Exception e) {
            log.warn("引用来源解析失败：{}", e.getMessage());
            return List.of();
        }
    }

    private Map<Long, String> topicNames(List<Long> topicIds) {
        List<Long> ids = topicIds == null ? List.of() : topicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return topicMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GeoTopic::getId, GeoTopic::getTopicName, (a, b) -> a));
    }

    private Map<Long, GeoMonitorRun> runsById(List<Long> runIds) {
        List<Long> ids = runIds == null ? List.of() : runIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return runMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GeoMonitorRun::getId, Function.identity(), (a, b) -> a));
    }

    private Map<String, Integer> platformOrderIndex() {
        List<GeoPlatform> platforms = platformMapper.selectList(new LambdaQueryWrapper<GeoPlatform>()
                .orderByAsc(GeoPlatform::getSortOrder));
        Map<String, Integer> index = new HashMap<>();
        for (GeoPlatform platform : platforms) {
            index.put(platform.getPlatformName(), platform.getSortOrder() == null ? 0 : platform.getSortOrder());
        }
        return index;
    }

    private List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (StringUtils.hasText(part)) {
                list.add(part.trim());
            }
        }
        return list;
    }

    private Set<String> splitToSet(String csv) {
        return splitCsv(csv).stream().collect(Collectors.toSet());
    }

    private String nz(String text) {
        return text == null ? "" : text;
    }
}
