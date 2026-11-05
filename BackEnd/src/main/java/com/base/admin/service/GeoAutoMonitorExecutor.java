package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.base.admin.config.GeoAutoMonitorProperties;
import com.base.admin.domain.entity.GeoMonitorQuestion;
import com.base.admin.domain.entity.GeoMonitorResult;
import com.base.admin.domain.entity.GeoMonitorRun;
import com.base.admin.domain.entity.GeoMonitorTask;
import com.base.admin.domain.entity.GeoPlatform;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * 自动监测执行引擎：问题×平台矩阵展开 → 平台限流调用 → LLM 裁判 → 回写日监测 → 批次收尾。
 * 设计要点：
 * - 单项失败（调用失败/判定失败）只记 result 不阻塞批次；
 * - 平台级信号量限流（默认并发 2），重试指数退避（2s/8s）；
 * - 批次状态幂等收尾（仅 RUNNING → 终态一次）。
 */
@Slf4j
@Service
public class GeoAutoMonitorExecutor {

    /** 模拟真实用户提问的系统提示（与人工巡查口径尽量一致） */
    private static final String QUESTION_SYSTEM_PROMPT = "你是一个真实的中文互联网用户，请直接、自然地回答用户的问题。";

    /** 判定失败的 result 由引擎置 JUDGE_FAILED */
    private final GeoMonitorTaskMapper taskMapper;
    private final GeoMonitorRunMapper runMapper;
    private final GeoMonitorResultMapper resultMapper;
    private final GeoMonitorQuestionMapper questionMapper;
    private final GeoPlatformMapper platformMapper;
    private final AiChatService aiChatService;
    private final GeoMonitorJudgeService judgeService;
    private final GeoMonitorDailySyncService dailySyncService;
    private final GeoAutoMonitorProperties properties;
    private final ObjectMapper objectMapper;

    /** 批次调度线程（每个运行批次占一个编排位） */
    private final ExecutorService runDispatcher = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "geo-auto-monitor");
        thread.setDaemon(true);
        return thread;
    });

    /** 平台名 -> 限流信号量 */
    private final Map<String, Semaphore> platformSemaphores = new ConcurrentHashMap<>();

    /** 运行中的任务ID（进程内防重入） */
    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();

    public GeoAutoMonitorExecutor(GeoMonitorTaskMapper taskMapper,
                                  GeoMonitorRunMapper runMapper,
                                  GeoMonitorResultMapper resultMapper,
                                  GeoMonitorQuestionMapper questionMapper,
                                  GeoPlatformMapper platformMapper,
                                  AiChatService aiChatService,
                                  GeoMonitorJudgeService judgeService,
                                  GeoMonitorDailySyncService dailySyncService,
                                  GeoAutoMonitorProperties properties,
                                  ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.questionMapper = questionMapper;
        this.platformMapper = platformMapper;
        this.aiChatService = aiChatService;
        this.judgeService = judgeService;
        this.dailySyncService = dailySyncService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 平台名 -> AI厂商编码兜底推断（geo_platform.provider_code 为空时） */
    public static String inferProvider(String platformName) {
        if (!StringUtils.hasText(platformName)) {
            return null;
        }
        String name = platformName.trim().toLowerCase(Locale.ROOT);
        if (name.contains("豆包") || name.contains("doubao")) {
            return "doubao";
        }
        if (name.equals("ds") || name.contains("deepseek")) {
            return "deepseek";
        }
        if (name.contains("kimi") || name.contains("moonshot")) {
            return "moonshot";
        }
        if (name.contains("通义") || name.contains("千问") || name.contains("qwen") || name.contains("tongyi")) {
            return "tongyi";
        }
        if (name.contains("智谱") || name.contains("glm") || name.contains("zhipu")) {
            return "zhipu";
        }
        return null;
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
        run.setStartTime(LocalDateTime.now());
        runMapper.insert(run);

        task.setLastRunId(run.getId());
        taskMapper.updateById(task);

        runningTaskIds.add(taskId);
        runDispatcher.submit(() -> executeRun(run, task, questions, platforms));
        return run;
    }

    private void executeRun(GeoMonitorRun run, GeoMonitorTask task,
                            List<GeoMonitorQuestion> questions, List<GeoPlatform> platforms) {
        int parallelism = Math.max(1, Math.min(6, platforms.size() * properties.getPerPlatformConcurrency()));
        ExecutorService pool = Executors.newFixedThreadPool(parallelism, r -> {
            Thread thread = new Thread(r, "geo-auto-run-" + run.getId());
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (GeoMonitorQuestion question : questions) {
                for (GeoPlatform platform : platforms) {
                    futures.add(pool.submit(() -> executeItemSafely(run, task, question, platform)));
                }
            }
            for (Future<?> future : futures) {
                future.get();
            }
            finalizeRun(run.getId(), task.getId());
        } catch (Exception e) {
            log.error("自动监测批次执行异常 runId={}", run.getId(), e);
            markRunFailed(run.getId(), truncate(e.getMessage(), 900));
        } finally {
            pool.shutdownNow();
            runningTaskIds.remove(task.getId());
        }
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
        result.setChannel("API");

        String provider = resolveProvider(platform);
        String answer = askWithRetry(question, platform, provider);
        if (answer == null) {
            result.setStatus(GeoMonitorResult.STATUS_CALL_FAILED);
            result.setErrorMsg("平台调用失败（已重试），详见服务日志");
            result.setLatencyMs((int) (System.currentTimeMillis() - start));
            resultMapper.insert(result);
            bumpCount(run.getId(), false);
            return;
        }
        result.setAnswerText(answer);
        result.setAnswerModel(provider == null ? "" : provider);

        try {
            GeoMonitorJudgeService.JudgeOutcome outcome = judgeService.judge(
                    question.getQuestionText(),
                    answer,
                    resolveWords(question.getBrandWords(), properties.getBrandWords()),
                    resolveWords(question.getCompetitorWords(), properties.getCompetitorWords()));
            result.setMentioned(outcome.mentioned());
            result.setRankNo(outcome.rankNo());
            result.setRecommendStatus(outcome.recommendStatus());
            result.setSentiment(outcome.sentiment());
            result.setMentionPosition(outcome.mentionPosition());
            result.setNegativeContent(outcome.negativeContent());
            result.setCompetitors(outcome.competitors());
            result.setCitations(objectMapper.writeValueAsString(outcome.citations()));
            result.setJudgeRaw(outcome.judgeRaw());
            result.setLatencyMs((int) (System.currentTimeMillis() - start));

            result.setStatus(GeoMonitorResult.STATUS_SUCCESS);
            resultMapper.insert(result);

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

    /** 平台调用（含重试与平台级限流）；成功返回回答原文，失败返回 null */
    private String askWithRetry(GeoMonitorQuestion question, GeoPlatform platform, String provider) {
        if (!StringUtils.hasText(provider) || !aiChatService.isProviderConfigured(provider)) {
            log.warn("平台 [{}] 未配置可用AI厂商（provider_code={}），跳过调用", platform.getPlatformName(), provider);
            return null;
        }
        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        for (int i = 0; i < attempts; i++) {
            try {
                Semaphore semaphore = platformSemaphores.computeIfAbsent(
                        platform.getPlatformName(), k -> new Semaphore(properties.getPerPlatformConcurrency()));
                semaphore.acquire();
                try {
                    return aiChatService.chat(QUESTION_SYSTEM_PROMPT, question.getQuestionText(), provider, 0.7);
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.warn("平台调用失败（第{}次）p={} q={} provider={}: {}",
                        i + 1, platform.getPlatformName(), question.getId(), provider, e.getMessage());
                if (i < attempts - 1) {
                    sleep(backoffMs(i));
                }
            }
        }
        return null;
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

    private String resolveProvider(GeoPlatform platform) {
        if (StringUtils.hasText(platform.getProviderCode())) {
            return platform.getProviderCode().trim();
        }
        return inferProvider(platform.getPlatformName());
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
