package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.base.admin.config.GeoAutoMonitorProperties;
import com.base.admin.config.GeoBrowserProbeProperties;
import com.base.admin.domain.dto.GeoWorkerAccountStatusDTO;
import com.base.admin.domain.dto.GeoWorkerFailDTO;
import com.base.admin.domain.dto.GeoWorkerHeartbeatDTO;
import com.base.admin.domain.entity.GeoMonitorJob;
import com.base.admin.domain.entity.GeoMonitorQuestion;
import com.base.admin.domain.entity.GeoMonitorResult;
import com.base.admin.domain.entity.GeoMonitorRun;
import com.base.admin.domain.entity.GeoMonitorTask;
import com.base.admin.domain.entity.GeoMonitorWorkerNode;
import com.base.admin.domain.entity.GeoPlatform;
import com.base.admin.domain.entity.GeoPlatformAccount;
import com.base.admin.domain.vo.GeoAutoResultDetailVO;
import com.base.admin.domain.vo.GeoWorkerAccountVO;
import com.base.admin.domain.vo.GeoWorkerBoardVO;
import com.base.admin.domain.vo.GeoWorkerClaimVO;
import com.base.admin.domain.vo.GeoWorkerNodeVO;
import com.base.admin.domain.vo.GeoWorkerProfileVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoMonitorJobMapper;
import com.base.admin.mapper.GeoMonitorQuestionMapper;
import com.base.admin.mapper.GeoMonitorResultMapper;
import com.base.admin.mapper.GeoMonitorRunMapper;
import com.base.admin.mapper.GeoMonitorTaskMapper;
import com.base.admin.mapper.GeoMonitorWorkerNodeMapper;
import com.base.admin.mapper.GeoPlatformAccountMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本机边缘采集：服务器只入队、校验令牌、裁判和回写日监测。
 * 提问发生在 Windows 上的 Chrome 用户目录里，不在 ECS 上启动浏览器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoMonitorWorkerService {

    public static final String REASON_NEED_LOGIN = "NEED_LOGIN";

    private final GeoAutoMonitorProperties properties;
    private final GeoBrowserProbeProperties browserProperties;
    private final GeoMonitorJobMapper jobMapper;
    private final GeoMonitorWorkerNodeMapper workerMapper;
    private final GeoMonitorRunMapper runMapper;
    private final GeoMonitorTaskMapper taskMapper;
    private final GeoMonitorQuestionMapper questionMapper;
    private final GeoMonitorResultMapper resultMapper;
    private final GeoPlatformAccountMapper accountMapper;
    private final GeoBrowserAccountService accountService;
    private final GeoMonitorJudgeService judgeService;
    private final GeoMonitorDailySyncService dailySyncService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public void requireToken(String token) {
        if (!properties.getWorker().isEnabled()) {
            throw new BusinessException("本机采集通道未开启");
        }
        String expected = properties.getWorker().getToken();
        if (!StringUtils.hasText(expected)) {
            throw new BusinessException(401, "服务器未配置 GEO_WORKER_TOKEN");
        }
        String given = token == null ? "" : token.trim();
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(401, "采集令牌无效");
        }
    }

    public static String normalizeNode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("采集电脑名称不能为空");
        }
        String node = raw.trim();
        if (!node.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new BusinessException("采集电脑名称只能包含字母、数字、点、下划线和中划线");
        }
        return node;
    }

    /** 一次运行展开成「问题 × 平台」队列，不在服务器上打开浏览器。 */
    @Transactional
    public void enqueue(GeoMonitorRun run, GeoMonitorTask task,
                        List<GeoMonitorQuestion> questions, List<GeoPlatform> platforms) {
        String shot = StringUtils.hasText(task.getScreenshotMode()) ? task.getScreenshotMode() : GeoMonitorTask.SHOT_MENTION;
        int deep = task.getDeepThink() != null && task.getDeepThink() == 1 ? 1 : 0;
        String region = task.getProbeRegion() == null ? "" : task.getProbeRegion();
        for (GeoMonitorQuestion question : questions) {
            for (GeoPlatform platform : platforms) {
                GeoMonitorJob job = new GeoMonitorJob();
                job.setRunId(run.getId());
                job.setTaskId(task.getId());
                job.setQuestionId(question.getId());
                job.setQuestionText(clip(question.getQuestionText(), 512));
                job.setPlatformId(platform.getId());
                job.setPlatformName(platform.getPlatformName());
                job.setStatus(GeoMonitorJob.STATUS_PENDING);
                job.setLeaseOwner("");
                job.setAttempt(0);
                job.setErrorMsg("");
                job.setDeepThink(deep);
                job.setScreenshotMode(shot);
                job.setProbeRegion(region);
                jobMapper.insert(job);
            }
        }
        log.info("自动监测已交给本机采集 runId={} jobs={}", run.getId(), questions.size() * platforms.size());
    }

    public void heartbeat(GeoWorkerHeartbeatDTO dto) {
        String node = normalizeNode(dto.getNode());
        GeoMonitorWorkerNode row = workerMapper.selectOne(new LambdaQueryWrapper<GeoMonitorWorkerNode>()
                .eq(GeoMonitorWorkerNode::getNodeName, node)
                .last("LIMIT 1"));
        if (row == null) {
            row = new GeoMonitorWorkerNode();
            row.setNodeName(node);
            row.setLastSeen(LocalDateTime.now());
            row.setCurrentJobId(dto.getCurrentJobId());
            row.setCurrentAccountId(dto.getCurrentAccountId());
            row.setVersion(clip(dto.getVersion(), 32));
            row.setRemark("");
            workerMapper.insert(row);
            return;
        }
        workerMapper.update(null, new LambdaUpdateWrapper<GeoMonitorWorkerNode>()
                .eq(GeoMonitorWorkerNode::getId, row.getId())
                .set(GeoMonitorWorkerNode::getLastSeen, LocalDateTime.now())
                .set(GeoMonitorWorkerNode::getVersion, clip(dto.getVersion(), 32))
                .setSql("current_job_id = " + sqlId(dto.getCurrentJobId())
                        + ", current_account_id = " + sqlId(dto.getCurrentAccountId())));
    }

    public GeoWorkerBoardVO board() {
        sweep();
        GeoWorkerBoardVO board = new GeoWorkerBoardVO();
        long offlineMs = Math.max(15_000L, properties.getWorker().getOfflineMs());
        LocalDateTime now = LocalDateTime.now();
        List<GeoWorkerNodeVO> nodes = new ArrayList<>();
        for (GeoMonitorWorkerNode row : workerMapper.selectList(new LambdaQueryWrapper<GeoMonitorWorkerNode>()
                .orderByDesc(GeoMonitorWorkerNode::getLastSeen))) {
            GeoWorkerNodeVO vo = new GeoWorkerNodeVO();
            vo.setNodeName(row.getNodeName());
            vo.setLastSeen(row.getLastSeen());
            vo.setCurrentJobId(row.getCurrentJobId());
            vo.setCurrentAccountId(row.getCurrentAccountId());
            vo.setVersion(row.getVersion());
            boolean online = row.getLastSeen() != null
                    && row.getLastSeen().plusNanos(offlineMs * 1_000_000L).isAfter(now);
            vo.setOnline(online);
            nodes.add(vo);
        }
        board.setNodes(nodes);
        board.setPendingJobs(countStatus(GeoMonitorJob.STATUS_PENDING));
        board.setLeasedJobs(countStatus(GeoMonitorJob.STATUS_LEASED));
        return board;
    }

    public List<GeoWorkerAccountVO> accountsFor(String nodeName) {
        String node = normalizeNode(nodeName);
        List<GeoWorkerAccountVO> list = new ArrayList<>();
        for (GeoPlatformAccount account : accountsOf(node)) {
            GeoWorkerAccountVO vo = new GeoWorkerAccountVO();
            vo.setAccountId(account.getId());
            vo.setPlatform(account.getPlatformName());
            vo.setAccountLabel(accountService.mask(account.getAccount()));
            vo.setLoginStatus(StringUtils.hasText(account.getBrowserLoginStatus())
                    ? account.getBrowserLoginStatus() : GeoBrowserAccountService.STATE_NONE);
            GeoBrowserProbeProperties.PlatformProfile profile = profileOf(account.getPlatformName());
            vo.setUrl(profile == null ? "" : profile.getUrl());
            list.add(vo);
        }
        return list;
    }

    public void reportAccountStatus(GeoWorkerAccountStatusDTO dto) {
        String node = normalizeNode(dto.getNode());
        GeoPlatformAccount account = accountService.require(dto.getAccountId());
        assertOwned(account, node);
        String status = dto.getStatus() == null ? "" : dto.getStatus().trim().toUpperCase();
        if (GeoBrowserAccountService.STATE_READY.equals(status)) {
            accountService.markProfileReady(account);
            return;
        }
        if (GeoBrowserAccountService.STATE_EXPIRED.equals(status)) {
            accountService.quarantine(account, dto.getError());
            return;
        }
        throw new BusinessException("登录状态只接受 READY 或 EXPIRED");
    }

    /** 领走一题。没有可做的题时返回 null。 */
    public synchronized GeoWorkerClaimVO claim(String nodeName) {
        String node = normalizeNode(nodeName);
        sweep();
        List<GeoMonitorJob> pending = jobMapper.selectList(new LambdaQueryWrapper<GeoMonitorJob>()
                .eq(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_PENDING)
                .orderByAsc(GeoMonitorJob::getId)
                .last("LIMIT 300"));
        for (GeoMonitorJob job : pending) {
            GeoBrowserProbeProperties.PlatformProfile profile = profileOf(job.getPlatformName());
            if (profile == null || !StringUtils.hasText(profile.getUrl())) {
                failTerminal(job, "平台「" + job.getPlatformName() + "」没有配置网页提问入口");
                continue;
            }
            GeoPlatformAccount account = pickAccount(job.getPlatformName(), node);
            if (account == null) {
                continue;
            }
            LocalDateTime until = LocalDateTime.now().plusNanos(Math.max(60_000L, properties.getWorker().getLeaseMs()) * 1_000_000L);
            int updated = jobMapper.update(null, new LambdaUpdateWrapper<GeoMonitorJob>()
                    .eq(GeoMonitorJob::getId, job.getId())
                    .eq(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_PENDING)
                    .set(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_LEASED)
                    .set(GeoMonitorJob::getAccountId, account.getId())
                    .set(GeoMonitorJob::getLeaseOwner, node)
                    .set(GeoMonitorJob::getLeaseUntil, until)
                    .set(GeoMonitorJob::getErrorMsg, ""));
            if (updated != 1) {
                continue;
            }
            accountService.touchUsed(account.getId());
            return toClaim(job, account, profile);
        }
        return null;
    }

    public void complete(String nodeName, Long jobId, String answerText, String reasoningText, String probeUrl,
                         String citationsJson, String diagnostic, MultipartFile screenshot) {
        String node = normalizeNode(nodeName);
        GeoMonitorJob job = requireLeased(jobId, node);
        if (!StringUtils.hasText(answerText)) {
            retryOrFail(job, "页面上没有取到回答");
            return;
        }
        GeoMonitorRun run = runMapper.selectById(job.getRunId());
        GeoMonitorTask task = taskMapper.selectById(job.getTaskId());
        GeoMonitorQuestion question = questionMapper.selectById(job.getQuestionId());
        if (run == null || task == null || question == null) {
            failTerminal(job, "任务或问题已被删除");
            return;
        }
        GeoMonitorJudgeService.JudgeOutcome judge = null;
        String judgeError = null;
        try {
            judge = judgeService.judge(
                    question.getQuestionText(),
                    clip(answerText, properties.getAnswerMaxChars()),
                    words(question.getBrandWords(), properties.getBrandWords()),
                    words(question.getCompetitorWords(), properties.getCompetitorWords()));
        } catch (Exception e) {
            judgeError = e.getMessage();
            log.warn("本机采集裁判失败 jobId={}: {}", job.getId(), e.getMessage());
        }
        String shotUrl = "";
        if (screenshot != null && !screenshot.isEmpty() && !GeoMonitorTask.SHOT_DISABLED.equals(job.getScreenshotMode())) {
            shotUrl = fileStorageService.saveGeoImage(screenshot);
        }
        boolean dropShot = GeoMonitorTask.SHOT_MENTION.equals(job.getScreenshotMode())
                && (judge == null || judge.mentioned() == null || judge.mentioned() == 0);
        if (dropShot && StringUtils.hasText(shotUrl)) {
            fileStorageService.deleteGeoUpload(shotUrl);
            shotUrl = "";
        }
        persistAnswer(job, run, task, question, node, answerText, reasoningText, probeUrl,
                citationsJson, diagnostic, shotUrl, judge, judgeError);
    }

    public void fail(GeoWorkerFailDTO dto) {
        String node = normalizeNode(dto.getNode());
        GeoMonitorJob job = requireLeased(dto.getJobId(), node);
        String message = StringUtils.hasText(dto.getMessage()) ? dto.getMessage().trim() : "本机采集失败";
        if (REASON_NEED_LOGIN.equalsIgnoreCase(dto.getReason())) {
            if (job.getAccountId() != null) {
                try {
                    accountService.quarantine(accountService.require(job.getAccountId()), message);
                } catch (Exception e) {
                    log.warn("淘汰登录态失败 accountId={}: {}", job.getAccountId(), e.getMessage());
                }
            }
            releasePending(job, message);
            return;
        }
        retryOrFail(job, message);
    }

    /** 收回过期领取，并收尾已经没有待做题目的批次。 */
    public void sweep() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }
        List<GeoMonitorJob> expired = jobMapper.selectList(new LambdaQueryWrapper<GeoMonitorJob>()
                .eq(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_LEASED)
                .lt(GeoMonitorJob::getLeaseUntil, LocalDateTime.now())
                .last("LIMIT 50"));
        for (GeoMonitorJob job : expired) {
            retryOrFail(job, "本机领取后未在时限内回传");
        }
        List<Long> runIds = jobMapper.selectList(new LambdaQueryWrapper<GeoMonitorJob>()
                        .select(GeoMonitorJob::getRunId)
                        .in(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_DONE, GeoMonitorJob.STATUS_FAILED)
                        .orderByDesc(GeoMonitorJob::getId)
                        .last("LIMIT 200"))
                .stream().map(GeoMonitorJob::getRunId).distinct().toList();
        for (Long runId : runIds) {
            finalizeRunIfIdle(runId);
        }
    }

    private void persistAnswer(GeoMonitorJob job, GeoMonitorRun run, GeoMonitorTask task, GeoMonitorQuestion question,
                               String node, String answerText, String reasoningText, String probeUrl,
                               String citationsJson, String diagnostic, String shotUrl,
                               GeoMonitorJudgeService.JudgeOutcome judge, String judgeError) {
        GeoPlatformAccount account = job.getAccountId() == null ? null : accountMapper.selectById(job.getAccountId());
        GeoMonitorResult result = new GeoMonitorResult();
        result.setRunId(run.getId());
        result.setQuestionId(question.getId());
        result.setQuestionText(question.getQuestionText());
        result.setTopicId(question.getTopicId());
        result.setPlatform(job.getPlatformName());
        result.setChannel("BROWSER");
        result.setPlatformAccountId(job.getAccountId());
        result.setPlatformAccountMask(account == null ? "" : accountService.mask(account.getAccount()));
        result.setDeepThink(job.getDeepThink() == null ? 0 : job.getDeepThink());
        result.setReasoningText(clip(reasoningText, 60_000));
        result.setProbeUrl(clip(probeUrl, 1000));
        result.setProbeRequest(diagnosticJson(node, job, diagnostic));
        result.setAnswerModel(clip(job.getPlatformName() + " 网页版", 64));
        result.setAnswerText(clip(answerText, 60_000));
        result.setCreateBy(node);
        if (StringUtils.hasText(shotUrl)) {
            result.setScreenshotUrl(shotUrl);
            result.setScreenshotType("BROWSER_SHOT");
        }
        boolean success = false;
        if (judge == null) {
            result.setStatus(GeoMonitorResult.STATUS_JUDGE_FAILED);
            result.setErrorMsg(clip(judgeError, 450));
        } else {
            result.setMentioned(judge.mentioned());
            result.setRankNo(judge.rankNo());
            result.setRecommendStatus(judge.recommendStatus());
            result.setSentiment(judge.sentiment());
            result.setMentionPosition(judge.mentionPosition());
            result.setNegativeContent(judge.negativeContent());
            result.setCompetitors(judge.competitors());
            result.setCitations(writeJson(mergeCitations(parseCitations(citationsJson), judge.citations())));
            result.setJudgeRaw(judge.judgeRaw());
            result.setStatus(GeoMonitorResult.STATUS_SUCCESS);
            success = true;
        }
        resultMapper.insert(result);
        if (success) {
            try {
                GeoMonitorDailySyncService.SyncOutcome sync = dailySyncService.sync(run, task, question, result);
                result.setDailyId(sync.dailyId());
                if (sync.skipped()) {
                    result.setStatus(GeoMonitorResult.STATUS_SKIPPED_MANUAL);
                }
                resultMapper.updateById(result);
            } catch (Exception e) {
                log.warn("本机采集回写日监测失败 jobId={}: {}", job.getId(), e.getMessage());
            }
        }
        jobMapper.update(null, new LambdaUpdateWrapper<GeoMonitorJob>()
                .eq(GeoMonitorJob::getId, job.getId())
                .eq(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_LEASED)
                .set(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_DONE)
                .set(GeoMonitorJob::getResultId, result.getId())
                .set(GeoMonitorJob::getErrorMsg, ""));
        bump(run.getId(), success || GeoMonitorResult.STATUS_SKIPPED_MANUAL.equals(result.getStatus()));
        finalizeRunIfIdle(run.getId());
    }

    private void retryOrFail(GeoMonitorJob job, String message) {
        int next = (job.getAttempt() == null ? 0 : job.getAttempt()) + 1;
        int max = Math.max(1, properties.getWorker().getMaxAttempts());
        if (next >= max) {
            failTerminal(job, message);
            return;
        }
        jobMapper.update(null, new LambdaUpdateWrapper<GeoMonitorJob>()
                .eq(GeoMonitorJob::getId, job.getId())
                .eq(GeoMonitorJob::getStatus, job.getStatus())
                .set(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_PENDING)
                .set(GeoMonitorJob::getAttempt, next)
                .set(GeoMonitorJob::getLeaseOwner, "")
                .set(GeoMonitorJob::getErrorMsg, clip(message, 500))
                .setSql("account_id = NULL, lease_until = NULL"));
        job.setAttempt(next);
        job.setStatus(GeoMonitorJob.STATUS_PENDING);
    }

    private void releasePending(GeoMonitorJob job, String message) {
        jobMapper.update(null, new LambdaUpdateWrapper<GeoMonitorJob>()
                .eq(GeoMonitorJob::getId, job.getId())
                .eq(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_LEASED)
                .set(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_PENDING)
                .set(GeoMonitorJob::getLeaseOwner, "")
                .set(GeoMonitorJob::getErrorMsg, clip(message, 500))
                .setSql("account_id = NULL, lease_until = NULL"));
    }

    private void failTerminal(GeoMonitorJob job, String message) {
        GeoMonitorRun run = runMapper.selectById(job.getRunId());
        if (run != null && job.getResultId() == null) {
            GeoMonitorResult result = new GeoMonitorResult();
            result.setRunId(run.getId());
            result.setQuestionId(job.getQuestionId());
            result.setQuestionText(job.getQuestionText());
            result.setPlatform(job.getPlatformName());
            result.setChannel("BROWSER");
            result.setPlatformAccountId(job.getAccountId());
            result.setAnswerModel(clip(job.getPlatformName() + " 网页版", 64));
            result.setStatus(GeoMonitorResult.STATUS_CALL_FAILED);
            result.setErrorMsg(clip(message, 450));
            result.setCreateBy(StringUtils.hasText(job.getLeaseOwner()) ? job.getLeaseOwner() : "worker");
            resultMapper.insert(result);
            jobMapper.update(null, new LambdaUpdateWrapper<GeoMonitorJob>()
                    .eq(GeoMonitorJob::getId, job.getId())
                    .set(GeoMonitorJob::getResultId, result.getId()));
            bump(run.getId(), false);
        }
        jobMapper.update(null, new LambdaUpdateWrapper<GeoMonitorJob>()
                .eq(GeoMonitorJob::getId, job.getId())
                .set(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_FAILED)
                .set(GeoMonitorJob::getErrorMsg, clip(message, 500))
                .set(GeoMonitorJob::getLeaseOwner, "")
                .setSql("lease_until = NULL"));
        if (run != null) {
            finalizeRunIfIdle(run.getId());
        }
    }

    private GeoMonitorJob requireLeased(Long jobId, String node) {
        if (jobId == null) {
            throw new BusinessException("队列ID不能为空");
        }
        GeoMonitorJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException("采集任务不存在");
        }
        if (!GeoMonitorJob.STATUS_LEASED.equals(job.getStatus()) || !node.equals(job.getLeaseOwner())) {
            throw new BusinessException("这题已不在当前电脑名下");
        }
        return job;
    }

    private GeoPlatformAccount pickAccount(String platform, String node) {
        if (!StringUtils.hasText(platform)) {
            return null;
        }
        Set<Long> busy = new LinkedHashSet<>();
        for (GeoMonitorJob leased : jobMapper.selectList(new LambdaQueryWrapper<GeoMonitorJob>()
                .eq(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_LEASED)
                .isNotNull(GeoMonitorJob::getAccountId))) {
            busy.add(leased.getAccountId());
        }
        List<GeoPlatformAccount> eligible = new ArrayList<>();
        for (GeoPlatformAccount account : accountsOf(node)) {
            if (!platform.equals(account.getPlatformName()) || busy.contains(account.getId())) {
                continue;
            }
            if (readyForWorker(account, node)) {
                eligible.add(account);
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        eligible.sort(Comparator
                .comparing(GeoPlatformAccount::getBrowserLastUsedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(GeoPlatformAccount::getId));
        return eligible.get(0);
    }

    private List<GeoPlatformAccount> accountsOf(String node) {
        return accountMapper.selectList(new LambdaQueryWrapper<GeoPlatformAccount>()
                .eq(GeoPlatformAccount::getWorkerNode, node)
                .eq(GeoPlatformAccount::getBrowserEnabled, 1)
                .eq(GeoPlatformAccount::getBrowserLoginType, GeoBrowserAccountService.LOGIN_PROFILE)
                .orderByAsc(GeoPlatformAccount::getId));
    }

    private boolean readyForWorker(GeoPlatformAccount account, String node) {
        if (!"正常".equals(account.getAccountStatus())) {
            return false;
        }
        if (!node.equals(account.getWorkerNode())) {
            return false;
        }
        int fails = account.getBrowserLoginFailCount() == null ? 0 : account.getBrowserLoginFailCount();
        if (fails >= GeoBrowserAccountService.MAX_CONSECUTIVE_FAILS) {
            return false;
        }
        return GeoBrowserAccountService.STATE_READY.equals(account.getBrowserLoginStatus());
    }

    private void assertOwned(GeoPlatformAccount account, String node) {
        if (!GeoBrowserAccountService.LOGIN_PROFILE.equalsIgnoreCase(account.getBrowserLoginType())
                || !node.equals(account.getWorkerNode())) {
            throw new BusinessException("该账号没有分配给这台采集电脑");
        }
    }

    private GeoWorkerClaimVO toClaim(GeoMonitorJob job, GeoPlatformAccount account,
                                     GeoBrowserProbeProperties.PlatformProfile profile) {
        GeoWorkerClaimVO vo = new GeoWorkerClaimVO();
        vo.setJobId(job.getId());
        vo.setAccountId(account.getId());
        vo.setAccountLabel(accountService.mask(account.getAccount()));
        vo.setPlatform(job.getPlatformName());
        vo.setQuestion(job.getQuestionText());
        vo.setDeepThink(job.getDeepThink() != null && job.getDeepThink() == 1 && profile.isDeepThinkSupported());
        vo.setScreenshot(!GeoMonitorTask.SHOT_DISABLED.equals(job.getScreenshotMode()) && profile.isScreenshotSupported());
        vo.setNavigationTimeoutMs(browserProperties.getNavigationTimeoutMs());
        vo.setAnswerTimeoutMs(browserProperties.getAnswerTimeoutMs());
        vo.setProfile(toProfile(profile));
        return vo;
    }

    private GeoWorkerProfileVO toProfile(GeoBrowserProbeProperties.PlatformProfile profile) {
        GeoWorkerProfileVO vo = new GeoWorkerProfileVO();
        vo.setName(profile.getName());
        vo.setUrl(profile.getUrl());
        vo.setInputSelector(profile.getInputSelector());
        vo.setSubmitSelector(profile.getSubmitSelector());
        vo.setAnswerSelector(profile.getAnswerSelector());
        vo.setStreamingSelector(profile.getStreamingSelector());
        vo.setDeepThinkSelector(profile.getDeepThinkSelector());
        vo.setReasoningSelector(profile.getReasoningSelector());
        vo.setReasoningContentSelector(profile.getReasoningContentSelector());
        vo.setShareSelector(profile.getShareSelector());
        vo.setShareLinkSelector(profile.getShareLinkSelector());
        vo.setCitationSelector(profile.getCitationSelector());
        vo.setLoginRequiredSelectors(profile.getLoginRequiredSelectors());
        vo.setDismissSelectors(profile.getDismissSelectors());
        vo.setAnswerRejectPatterns(profile.getAnswerRejectPatterns());
        return vo;
    }

    private GeoBrowserProbeProperties.PlatformProfile profileOf(String platform) {
        if (!StringUtils.hasText(platform)) {
            return null;
        }
        return browserProperties.effectiveProfiles().get(platform.trim());
    }

    private void bump(Long runId, boolean success) {
        runMapper.update(null, new LambdaUpdateWrapper<GeoMonitorRun>()
                .eq(GeoMonitorRun::getId, runId)
                .setSql("done_count = done_count + 1")
                .setSql(success ? "success_count = success_count + 1" : "failed_count = failed_count + 1"));
    }

    private void finalizeRunIfIdle(Long runId) {
        Long open = jobMapper.selectCount(new LambdaQueryWrapper<GeoMonitorJob>()
                .eq(GeoMonitorJob::getRunId, runId)
                .in(GeoMonitorJob::getStatus, GeoMonitorJob.STATUS_PENDING, GeoMonitorJob.STATUS_LEASED));
        if (open != null && open > 0) {
            return;
        }
        GeoMonitorRun run = runMapper.selectById(runId);
        if (run == null || !GeoMonitorRun.STATUS_RUNNING.equals(run.getStatus())) {
            return;
        }
        String status = run.getFailedCount() == null || run.getFailedCount() == 0
                ? GeoMonitorRun.STATUS_SUCCESS
                : (run.getSuccessCount() == null || run.getSuccessCount() == 0
                ? GeoMonitorRun.STATUS_FAILED : GeoMonitorRun.STATUS_PARTIAL);
        int updated = runMapper.update(null, new LambdaUpdateWrapper<GeoMonitorRun>()
                .eq(GeoMonitorRun::getId, runId)
                .eq(GeoMonitorRun::getStatus, GeoMonitorRun.STATUS_RUNNING)
                .set(GeoMonitorRun::getStatus, status)
                .set(GeoMonitorRun::getEndTime, LocalDateTime.now()));
        if (updated != 1) {
            return;
        }
        GeoMonitorTask task = taskMapper.selectById(run.getTaskId());
        if (task != null) {
            task.setNextRunTime(GeoAutoMonitorExecutor.computeNextRunTime(task, LocalDateTime.now()));
            taskMapper.updateById(task);
        }
        log.info("本机采集批次完成 runId={} status={}", runId, status);
    }

    private int countStatus(String status) {
        Long n = jobMapper.selectCount(new LambdaQueryWrapper<GeoMonitorJob>().eq(GeoMonitorJob::getStatus, status));
        return n == null ? 0 : n.intValue();
    }

    private String diagnosticJson(String node, GeoMonitorJob job, String diagnostic) {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("node", node);
        diag.put("jobId", job.getId());
        diag.put("accountId", job.getAccountId());
        diag.put("channel", "EDGE_PROFILE");
        diag.put("note", diagnostic == null ? "" : clip(diagnostic, 4000));
        return clip(writeJson(diag), 60_000);
    }

    private List<GeoAutoResultDetailVO.Citation> parseCitations(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<GeoAutoResultDetailVO.Citation> list = objectMapper.readValue(json,
                    new TypeReference<List<GeoAutoResultDetailVO.Citation>>() {
                    });
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<GeoAutoResultDetailVO.Citation> mergeCitations(List<GeoAutoResultDetailVO.Citation> page,
                                                                List<GeoAutoResultDetailVO.Citation> judge) {
        List<GeoAutoResultDetailVO.Citation> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addCitations(merged, seen, page);
        addCitations(merged, seen, judge);
        return merged.size() > 30 ? merged.subList(0, 30) : merged;
    }

    private void addCitations(List<GeoAutoResultDetailVO.Citation> merged, Set<String> seen,
                              List<GeoAutoResultDetailVO.Citation> source) {
        if (source == null) {
            return;
        }
        for (GeoAutoResultDetailVO.Citation item : source) {
            if (item == null) {
                continue;
            }
            String key = StringUtils.hasText(item.getUrl()) ? item.getUrl() : item.getTitle();
            if (!StringUtils.hasText(key) || !seen.add(key)) {
                continue;
            }
            merged.add(item);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String words(String questionLevel, String globalDefault) {
        return StringUtils.hasText(questionLevel) ? questionLevel.trim()
                : (globalDefault == null ? "" : globalDefault.trim());
    }

    /** 只拼 Long 或 NULL，避免把心跳里的空闲状态更新丢弃。 */
    private static String sqlId(Long id) {
        return id == null ? "NULL" : Long.toString(id);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
