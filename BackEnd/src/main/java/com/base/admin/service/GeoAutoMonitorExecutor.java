package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.base.admin.config.GeoAutoMonitorProperties;
import com.base.admin.domain.entity.GeoMonitorQuestion;
import com.base.admin.domain.entity.GeoMonitorResult;
import com.base.admin.domain.entity.GeoMonitorRun;
import com.base.admin.domain.entity.GeoMonitorTask;
import com.base.admin.domain.entity.GeoPlatform;
import com.base.admin.domain.vo.GeoAutoResultDetailVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoMonitorQuestionMapper;
import com.base.admin.mapper.GeoMonitorResultMapper;
import com.base.admin.mapper.GeoMonitorRunMapper;
import com.base.admin.mapper.GeoMonitorTaskMapper;
import com.base.admin.mapper.GeoPlatformMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 自动监测执行引擎：问题×平台矩阵展开 → C端网页版浏览器采集 → LLM 裁判 → 回写日监测 → 批次收尾。
 * 设计要点：
 * - 采集通道为 BROWSER（对标模力指数），真实会话链接/整页截图/思考过程/引用信源全部留档；
 * - 单项失败（采集失败/判定失败）只记 result 不阻塞批次；
 * - 浏览器采集器内部单线程串行，采集重试指数退避（2s/8s）；
 * - 批次状态幂等收尾（仅 RUNNING → 终态一次）。
 */
@Slf4j
@Service
public class GeoAutoMonitorExecutor {

    /** 判定失败的 result 由引擎置 JUDGE_FAILED */
    private final GeoMonitorTaskMapper taskMapper;
    private final GeoMonitorRunMapper runMapper;
    private final GeoMonitorResultMapper resultMapper;
    private final GeoMonitorQuestionMapper questionMapper;
    private final GeoPlatformMapper platformMapper;
    private final GeoBrowserProbeService probeService;
    private final GeoMonitorJudgeService judgeService;
    private final GeoMonitorDailySyncService dailySyncService;
    private final GeoAutoMonitorProperties properties;
    private final ObjectMapper objectMapper;

    /** 批次编排单线程：全局同一时刻只跑一个任务的一个批次，其余任务排队，避免 ECS 叠加满载 */
    private final ExecutorService runDispatcher = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "geo-auto-monitor");
        thread.setDaemon(true);
        return thread;
    });

    /** 运行中的任务ID（进程内防重入） */
    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();

    public GeoAutoMonitorExecutor(GeoMonitorTaskMapper taskMapper,
                                  GeoMonitorRunMapper runMapper,
                                  GeoMonitorResultMapper resultMapper,
                                  GeoMonitorQuestionMapper questionMapper,
                                  GeoPlatformMapper platformMapper,
                                  GeoBrowserProbeService probeService,
                                  GeoMonitorJudgeService judgeService,
                                  GeoMonitorDailySyncService dailySyncService,
                                  GeoAutoMonitorProperties properties,
                                  ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.questionMapper = questionMapper;
        this.platformMapper = platformMapper;
        this.probeService = probeService;
        this.judgeService = judgeService;
        this.dailySyncService = dailySyncService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isRunning(Long taskId) {
        return runningTaskIds.contains(taskId);
    }

    /** 展开任务圈选的启用问题（TOPIC 按话题 / QUESTION 按问题ID） */
    public List<GeoMonitorQuestion> resolveQuestions(GeoMonitorTask task) {
        List<Long> ids = parseIds(task.getScopeIds());
        if (ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<GeoMonitorQuestion> wrapper = new LambdaQueryWrapper<GeoMonitorQuestion>()
                .eq(GeoMonitorQuestion::getEnabled, 1)
                .orderByAsc(GeoMonitorQuestion::getSortOrder)
                .orderByAsc(GeoMonitorQuestion::getId);
        if (GeoMonitorTask.SCOPE_QUESTION.equals(task.getScopeType())) {
            wrapper.in(GeoMonitorQuestion::getId, ids);
        } else {
            wrapper.in(GeoMonitorQuestion::getTopicId, ids);
        }
        return questionMapper.selectList(wrapper);
    }

    /** 展开任务配置的平台（须为 geo_platform 中存在的平台） */
    public List<GeoPlatform> resolvePlatforms(GeoMonitorTask task) {
        List<String> names = parseNames(task.getPlatforms());
        if (names.isEmpty()) {
            return List.of();
        }
        List<GeoPlatform> platforms = platformMapper.selectList(new LambdaQueryWrapper<GeoPlatform>()
                .in(GeoPlatform::getPlatformName, names)
                .orderByAsc(GeoPlatform::getSortOrder));
        List<String> missing = names.stream()
                .filter(n -> platforms.stream().noneMatch(p -> p.getPlatformName().equals(n)))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("以下平台不存在，请先到平台管理维护：" + String.join("、", missing));
        }
        return platforms;
    }

    /** 启动一次运行（SCHEDULED/MANUAL），创建批次并异步执行；返回批次。 */
    public synchronized GeoMonitorRun startRun(Long taskId, String triggerType) {
        GeoMonitorTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (runningTaskIds.contains(taskId)) {
            throw new BusinessException("该任务正在运行中，请等待完成");
        }
        Long runningCount = runMapper.selectCount(new LambdaQueryWrapper<GeoMonitorRun>()
                .eq(GeoMonitorRun::getTaskId, taskId)
                .eq(GeoMonitorRun::getStatus, GeoMonitorRun.STATUS_RUNNING));
        if (runningCount != null && runningCount > 0) {
            throw new BusinessException("该任务存在进行中的批次（可能服务重启残留），请稍后再试");
        }

        List<GeoMonitorQuestion> questions = resolveQuestions(task);
        if (questions.isEmpty()) {
            throw new BusinessException("任务圈选范围内没有启用的问题");
        }
        List<GeoPlatform> platforms = resolvePlatforms(task);
        if (platforms.isEmpty()) {
            throw new BusinessException("任务未配置有效平台");
        }

        GeoMonitorRun run = new GeoMonitorRun();
        run.setTaskId(task.getId());
        run.setTaskName(task.getTaskName());
        run.setRunDate(LocalDateTime.now().toLocalDate());
        run.setTriggerType(triggerType);
        run.setStatus(GeoMonitorRun.STATUS_RUNNING);
        run.setTotalCount(questions.size() * platforms.size());
        run.setDoneCount(0);
        run.setSuccessCount(0);
        run.setFailedCount(0);
        // v28 分批串行：每批只跑 batchSize 个问题，跑完一批再起下一批
        int batchSize = Math.max(1, properties.getBatchSize());
        run.setBatchSize(batchSize);
        run.setBatchTotal((questions.size() + batchSize - 1) / batchSize);
        run.setBatchDone(0);
        run.setCurrentBatchNo(0);
        run.setStartTime(LocalDateTime.now());
        runMapper.insert(run);

        task.setLastRunId(run.getId());
        taskMapper.updateById(task);

        runningTaskIds.add(taskId);
        runDispatcher.submit(() -> executeRun(run, task, questions, platforms));
        return run;
    }

    /**
     * v28 分批串行调度：把问题按 batchSize（默认 3）切批，一批跑完才起下一批，
     * 循环直到队列耗尽；批次间与相邻监测项之间都有可配置冷却，避免 ECS 持续满负荷。
     *
     * <p>浏览器采集器本身是单线程串行，登录态按平台缓存，所以同批内同平台只登录一次；
     * 这里不再开并发线程池，彻底消除多浏览器实例抢资源的问题。
     */
    private void executeRun(GeoMonitorRun run, GeoMonitorTask task,
                            List<GeoMonitorQuestion> questions, List<GeoPlatform> platforms) {
        int batchSize = run.getBatchSize() == null || run.getBatchSize() < 1
                ? Math.max(1, properties.getBatchSize()) : run.getBatchSize();
        List<List<GeoMonitorQuestion>> batches = new ArrayList<>();
        for (int i = 0; i < questions.size(); i += batchSize) {
            batches.add(new ArrayList<>(questions.subList(i, Math.min(i + batchSize, questions.size()))));
        }
        try {
            for (int b = 0; b < batches.size(); b++) {
                if (Thread.currentThread().isInterrupted()) {
                    log.warn("自动监测批次被中断 runId={} 已完成 {}/{}", run.getId(), b, batches.size());
                    break;
                }
                List<GeoMonitorQuestion> batch = batches.get(b);
                int batchNo = b + 1;
                long batchStart = System.currentTimeMillis();
                markBatchStart(run.getId(), batchNo);
                log.info("自动监测批次开始 runId={} batch={}/{} questions={} platforms={}",
                        run.getId(), batchNo, batches.size(), batch.size(), platforms.size());

                for (GeoMonitorQuestion question : batch) {
                    for (GeoPlatform platform : platforms) {
                        executeItemSafely(run, task, question, platform);
                        if (properties.getBatchItemIntervalMs() > 0) {
                            sleep(properties.getBatchItemIntervalMs());
                        }
                    }
                }

                long cost = System.currentTimeMillis() - batchStart;
                markBatchDone(run.getId(), batchNo);
                boolean slow = cost > properties.getBatchMaxDurationMs();
                if (slow) {
                    log.warn("自动监测批次耗时超阈值 runId={} batch={} cost={}ms threshold={}ms，追加冷却",
                            run.getId(), batchNo, cost, properties.getBatchMaxDurationMs());
                }
                Integer doneNow = runMapper.selectById(run.getId()).getDoneCount();
                log.info("自动监测批次完成 runId={} batch={}/{} cost={}ms done={}/{}",
                        run.getId(), batchNo, batches.size(), cost, doneNow, run.getTotalCount());

                if (b < batches.size() - 1) {
                    long cooldown = Math.max(0, properties.getBatchIntervalMs()) * (slow ? 2 : 1);
                    if (cooldown > 0) {
                        sleep(cooldown);
                    }
                }
            }
            finalizeRun(run.getId(), task.getId());
        } catch (Exception e) {
            log.error("自动监测批次执行异常 runId={}", run.getId(), e);
            markRunFailed(run.getId(), truncate(e.getMessage(), 900));
        } finally {
            runningTaskIds.remove(task.getId());
        }
    }

    /** 批次开始：更新当前批次号（前端运行记录可看到实时进度） */
    private void markBatchStart(Long runId, int batchNo) {
        runMapper.update(null, new LambdaUpdateWrapper<GeoMonitorRun>()
                .eq(GeoMonitorRun::getId, runId)
                .set(GeoMonitorRun::getCurrentBatchNo, batchNo));
    }

    /** 批次结束：原子累加已完成批次数 */
    private void markBatchDone(Long runId, int batchNo) {
        runMapper.update(null, new LambdaUpdateWrapper<GeoMonitorRun>()
                .eq(GeoMonitorRun::getId, runId)
                .setSql("batch_done = batch_done + 1")
                .set(GeoMonitorRun::getCurrentBatchNo, batchNo));
    }

    private void executeItemSafely(GeoMonitorRun run, GeoMonitorTask task,
                                   GeoMonitorQuestion question, GeoPlatform platform) {
        try {
            executeItem(run, task, question, platform);
        } catch (Exception e) {
            log.error("监测项异常 runId={} q={} p={}", run.getId(), question.getId(), platform.getPlatformName(), e);
            bumpCount(run.getId(), false);
        }
    }

    private void executeItem(GeoMonitorRun run, GeoMonitorTask task,
                             GeoMonitorQuestion question, GeoPlatform platform) {
        long start = System.currentTimeMillis();
        GeoMonitorResult result = new GeoMonitorResult();
        result.setRunId(run.getId());
        result.setQuestionId(question.getId());
        result.setQuestionText(question.getQuestionText());
        result.setTopicId(question.getTopicId());
        result.setPlatform(platform.getPlatformName());
        result.setChannel("BROWSER");

        String shotMode = StringUtils.hasText(task.getScreenshotMode())
                ? task.getScreenshotMode() : GeoMonitorTask.SHOT_MENTION;
        boolean deepThink = task.getDeepThink() != null && task.getDeepThink() == 1;
        boolean wantScreenshot = !GeoMonitorTask.SHOT_DISABLED.equals(shotMode);

        GeoBrowserProbeService.ProbeOutcome outcome =
                probeWithRetry(question, platform, deepThink, wantScreenshot, task.getProbeRegion());

        // 取证信息即使采集失败也尽量留档，便于排查选择器/登录态
        result.setDeepThink(outcome.deepThinkApplied() ? 1 : 0);
        result.setReasoningText(truncate(outcome.reasoningText(), 60_000));
        result.setProbeUrl(truncate(outcome.probeUrl(), 1000));
        result.setProbeRequest(truncate(outcome.diagnostic(), 60_000));
        result.setAnswerModel(outcome.modelLabel());
        result.setAnswerText(outcome.answerText());
        if (StringUtils.hasText(outcome.screenshotUrl())) {
            result.setScreenshotUrl(outcome.screenshotUrl());
            result.setScreenshotType(outcome.screenshotType());
        }

        if (!outcome.success()) {
            result.setStatus(GeoMonitorResult.STATUS_CALL_FAILED);
            result.setErrorMsg(truncate(outcome.error(), 450));
            result.setLatencyMs((int) (System.currentTimeMillis() - start));
            resultMapper.insert(result);
            bumpCount(run.getId(), false);
            return;
        }

        try {
            GeoMonitorJudgeService.JudgeOutcome judgeOutcome = judgeService.judge(
                    question.getQuestionText(),
                    outcome.answerText(),
                    resolveWords(question.getBrandWords(), properties.getBrandWords()),
                    resolveWords(question.getCompetitorWords(), properties.getCompetitorWords()));
            result.setMentioned(judgeOutcome.mentioned());
            result.setRankNo(judgeOutcome.rankNo());
            result.setRecommendStatus(judgeOutcome.recommendStatus());
            result.setSentiment(judgeOutcome.sentiment());
            result.setMentionPosition(judgeOutcome.mentionPosition());
            result.setNegativeContent(judgeOutcome.negativeContent());
            result.setCompetitors(judgeOutcome.competitors());
            // 页面 DOM 抓到的信源带真实链接，LLM 裁判的输出作补充
            result.setCitations(objectMapper.writeValueAsString(
                    mergeCitations(outcome.citations(), judgeOutcome.citations())));
            result.setJudgeRaw(judgeOutcome.judgeRaw());
            result.setLatencyMs((int) (System.currentTimeMillis() - start));
            result.setStatus(GeoMonitorResult.STATUS_SUCCESS);
            resultMapper.insert(result);

            // 「提及截图」模式：品牌未出现则不留证，删除已生成的整页截图
            if (GeoMonitorTask.SHOT_MENTION.equals(shotMode)
                    && (judgeOutcome.mentioned() == null || judgeOutcome.mentioned() == 0)
                    && StringUtils.hasText(result.getScreenshotUrl())) {
                probeService.discardScreenshot(result.getScreenshotUrl());
                result.setScreenshotUrl("");
                result.setScreenshotType("");
            }

            GeoMonitorDailySyncService.SyncOutcome syncOutcome = dailySyncService.sync(run, task, question, result);
            result.setDailyId(syncOutcome.dailyId());
            if (syncOutcome.skipped()) {
                result.setStatus(GeoMonitorResult.STATUS_SKIPPED_MANUAL);
            }
            resultMapper.updateById(result);
            bumpCount(run.getId(), true);
        } catch (Exception e) {
            log.warn("裁判判定失败 runId={} q={} p={}: {}", run.getId(), question.getId(),
                    platform.getPlatformName(), e.getMessage());
            result.setStatus(GeoMonitorResult.STATUS_JUDGE_FAILED);
            result.setErrorMsg(truncate(e.getMessage(), 450));
            result.setLatencyMs((int) (System.currentTimeMillis() - start));
            resultMapper.insert(result);
            bumpCount(run.getId(), false);
        }
    }

    /** 浏览器采集（含重试）；失败也返回带诊断的 outcome，由引擎记录 CALL_FAILED */
    private GeoBrowserProbeService.ProbeOutcome probeWithRetry(GeoMonitorQuestion question,
                                                               GeoPlatform platform,
                                                               boolean deepThink,
                                                               boolean screenshot,
                                                               String region) {
        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        GeoBrowserProbeService.ProbeOutcome last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                GeoBrowserProbeService.ProbeOutcome outcome = probeService.probe(
                        platform.getPlatformName(), question.getQuestionText(),
                        deepThink, screenshot, region, null);
                if (outcome.success()) {
                    return outcome;
                }
                last = outcome;
                log.warn("浏览器采集失败（第{}次）p={} q={}: {}",
                        i + 1, platform.getPlatformName(), question.getId(), outcome.error());
            } catch (Exception e) {
                log.warn("浏览器采集异常（第{}次）p={} q={}: {}",
                        i + 1, platform.getPlatformName(), question.getId(), e.getMessage());
            }
            if (i < attempts - 1) {
                sleep(backoffMs(i));
            }
        }
        return last != null ? last : probeService.failed(platform.getPlatformName(),
                question.getQuestionText(), "浏览器采集异常（已重试），详见服务日志与后台诊断");
    }

    /** 页面信源（带链接）优先，裁判补充的信源去重后追加 */
    private List<GeoAutoResultDetailVO.Citation> mergeCitations(
            List<GeoBrowserProbeService.Citation> pageCitations,
            List<GeoAutoResultDetailVO.Citation> judgeCitations) {
        List<GeoAutoResultDetailVO.Citation> merged = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (GeoBrowserProbeService.Citation c : pageCitations) {
            String key = StringUtils.hasText(c.url()) ? c.url() : c.title();
            if (!StringUtils.hasText(key) || !seen.add(key)) {
                continue;
            }
            GeoAutoResultDetailVO.Citation item = new GeoAutoResultDetailVO.Citation();
            item.setTitle(c.title());
            item.setSite(c.site());
            item.setUrl(c.url());
            item.setContext(c.context());
            merged.add(item);
        }
        for (GeoAutoResultDetailVO.Citation c : judgeCitations) {
            String key = StringUtils.hasText(c.getUrl()) ? c.getUrl() : c.getTitle();
            if (!StringUtils.hasText(key) || !seen.add(key)) {
                continue;
            }
            merged.add(c);
        }
        return merged.size() > 30 ? merged.subList(0, 30) : merged;
    }

    /** 重试退避：2s、8s（指数） */
    private long backoffMs(int attemptIndex) {
        return 2000L * (1L << attemptIndex);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 单项计数（原子 SQL 自增） */
    private void bumpCount(Long runId, boolean success) {
        LambdaUpdateWrapper<GeoMonitorRun> wrapper = new LambdaUpdateWrapper<GeoMonitorRun>()
                .eq(GeoMonitorRun::getId, runId)
                .setSql("done_count = done_count + 1")
                .setSql(success ? "success_count = success_count + 1" : "failed_count = failed_count + 1");
        runMapper.update(null, wrapper);
    }

    /** 批次收尾：RUNNING -> 终态（幂等，仅一次生效） */
    private void finalizeRun(Long runId, Long taskId) {
        GeoMonitorRun run = runMapper.selectById(runId);
        if (run == null || !GeoMonitorRun.STATUS_RUNNING.equals(run.getStatus())) {
            return;
        }
        String status = run.getFailedCount() == 0
                ? GeoMonitorRun.STATUS_SUCCESS
                : (run.getSuccessCount() == 0 ? GeoMonitorRun.STATUS_FAILED : GeoMonitorRun.STATUS_PARTIAL);
        int updated = runMapper.update(null, new LambdaUpdateWrapper<GeoMonitorRun>()
                .eq(GeoMonitorRun::getId, runId)
                .eq(GeoMonitorRun::getStatus, GeoMonitorRun.STATUS_RUNNING)
                .set(GeoMonitorRun::getStatus, status)
                .set(GeoMonitorRun::getEndTime, LocalDateTime.now()));
        if (updated == 1) {
            scheduleNextRun(taskId);
            log.info("自动监测批次完成 runId={} status={} success={} failed={}",
                    runId, status, run.getSuccessCount(), run.getFailedCount());
        }
    }

    private void markRunFailed(Long runId, String errorMsg) {
        runMapper.update(null, new LambdaUpdateWrapper<GeoMonitorRun>()
                .eq(GeoMonitorRun::getId, runId)
                .eq(GeoMonitorRun::getStatus, GeoMonitorRun.STATUS_RUNNING)
                .set(GeoMonitorRun::getStatus, GeoMonitorRun.STATUS_FAILED)
                .set(GeoMonitorRun::getEndTime, LocalDateTime.now())
                .set(GeoMonitorRun::getErrorMsg, errorMsg));
    }

    /** 计算并落库任务下次运行时间 */
    public void scheduleNextRun(Long taskId) {
        GeoMonitorTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setNextRunTime(computeNextRunTime(task, LocalDateTime.now()));
        taskMapper.updateById(task);
    }

    /** 下次运行时间：每日=当日 runTime（已过则次日）；每周=下一个目标星期几的 runTime */
    public static LocalDateTime computeNextRunTime(GeoMonitorTask task, LocalDateTime now) {
        LocalTime runTime = task.getRunTime() != null ? task.getRunTime() : LocalTime.of(8, 30);
        if (GeoMonitorTask.FREQ_WEEKLY.equals(task.getFreq())) {
            int target = task.getWeekDay() == null ? 1 : Math.min(7, Math.max(1, task.getWeekDay()));
            for (int i = 0; i < 8; i++) {
                LocalDateTime candidate = now.toLocalDate().plusDays(i).atTime(runTime);
                if (candidate.getDayOfWeek().getValue() == target && candidate.isAfter(now)) {
                    return candidate;
                }
            }
            return now.toLocalDate().plusWeeks(1).atTime(runTime);
        }
        LocalDateTime candidate = now.toLocalDate().atTime(runTime);
        return candidate.isAfter(now) ? candidate : candidate.plusDays(1);
    }

    private String resolveWords(String questionLevel, String globalDefault) {
        return StringUtils.hasText(questionLevel) ? questionLevel.trim() : (globalDefault == null ? "" : globalDefault.trim());
    }

    private List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (!StringUtils.hasText(csv)) {
            return ids;
        }
        for (String part : csv.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 脏数据忽略
            }
        }
        return ids;
    }

    private List<String> parseNames(String csv) {
        List<String> names = new ArrayList<>();
        if (!StringUtils.hasText(csv)) {
            return names;
        }
        for (String part : csv.split(",")) {
            if (StringUtils.hasText(part)) {
                names.add(part.trim());
            }
        }
        return names;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 供调度器使用：遍历到期任务 */
    public void forEachDueTask(Consumer<GeoMonitorTask> consumer) {
        List<GeoMonitorTask> due = taskMapper.selectList(new LambdaQueryWrapper<GeoMonitorTask>()
                .eq(GeoMonitorTask::getEnabled, 1)
                .isNotNull(GeoMonitorTask::getNextRunTime)
                .le(GeoMonitorTask::getNextRunTime, LocalDateTime.now())
                .orderByAsc(GeoMonitorTask::getNextRunTime)
                .last("LIMIT 20"));
        due.forEach(consumer);
    }
}
