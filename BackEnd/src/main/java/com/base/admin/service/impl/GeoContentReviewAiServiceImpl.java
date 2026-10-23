package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.GeoContentReviewAiBatchDTO;
import com.base.admin.domain.dto.GeoContentReviewAiFeedbackDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoContentReview;
import com.base.admin.domain.entity.GeoContentReviewAi;
import com.base.admin.domain.entity.GeoContentReviewAnnotation;
import com.base.admin.domain.entity.GeoContentReviewer;
import com.base.admin.domain.vo.GeoContentReviewAiIssueVO;
import com.base.admin.domain.vo.GeoContentReviewAiVO;
import com.base.admin.domain.vo.GeoContentReviewAnnotationVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.GeoContentReviewAiMapper;
import com.base.admin.mapper.GeoContentReviewAnnotationMapper;
import com.base.admin.mapper.GeoContentReviewMapper;
import com.base.admin.mapper.GeoContentReviewerMapper;
import com.base.admin.service.AiChatService;
import com.base.admin.service.GeoContentReviewAiService;
import com.base.admin.service.GeoContentReviewRuleService;
import com.base.admin.service.GeoReviewKbService;
import com.base.admin.service.SysTaskService;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoContentReviewAiServiceImpl implements GeoContentReviewAiService {

    private static final String SYSTEM_PROMPT = """
            你是小巴依 GEO 内容投放的文章预审助手。必须依据系统提供的「审稿知识库」口径检查文章。
            冲突时以知识库中的「撰写注意点」为准，其次是品牌/产品/门店事实，再次是 AI 友好度与文章结构要求。
            只输出一个 JSON 对象，不要 markdown，不要解释。格式：
            {"suggestion":"approve或reject","score":0到100的整数,"summary":"一句话风险摘要","issues":[{"quoteText":"原文片段","commentText":"问题说明","level":"high或mid或low","quotePrefix":"选区前若干字可选","quoteSuffix":"选区后若干字可选"}]}
            规则：
            - suggestion=approve 表示整体可过；reject 表示建议退回修改。
            - 出现禁词、错误品牌事实、错误产品口径、结构严重不合 GEO 要求时，应 reject 并在 issues 划出原文。
            - score 越高质量越好；事实错误、违规、跑题、空泛灌水应偏低并 reject。
            - issues 只列需要划词指出的具体问题，approve 时可为空数组；每条 quoteText 必须能在正文中找到原句，不要编造。
            - summary 用中文，不超过 200 字；commentText 简洁可执行，可引用知识库口径。
            """;

    private final GeoContentReviewAiMapper aiMapper;
    private final GeoContentReviewMapper reviewMapper;
    private final GeoContentPlacementMapper placementMapper;
    private final GeoContentReviewerMapper reviewerMapper;
    private final GeoContentReviewAnnotationMapper annotationMapper;
    private final GeoContentReviewRuleService reviewRuleService;
    private final GeoReviewKbService reviewKbService;
    private final AiChatService aiChatService;
    private final SysTaskService taskService;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "geo-review-ai");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public void enqueueAfterSubmit(Long reviewId) {
        if (reviewId == null) {
            return;
        }
        GeoContentReview review = reviewMapper.selectById(reviewId);
        if (review == null || !"pending".equals(review.getStatus())) {
            return;
        }
        GeoContentPlacement placement = placementMapper.selectById(review.getPlacementId());
        if (placement == null) {
            return;
        }
        if (!reviewRuleService.aiPreReviewEnabled(placement.getOwnerUserId())) {
            return;
        }
        if (!aiChatService.isEnabled()) {
            log.info("AI 预审跳过：未配置可用模型 reviewId={}", reviewId);
            return;
        }
        GeoContentReviewAi row = ensureRow(review, false);
        if (row == null) {
            return;
        }
        if ("running".equals(row.getStatus())) {
            return;
        }
        scheduleRun(row.getId());
    }

    @Override
    public GeoContentReviewAiVO getByReviewId(Long reviewId) {
        if (reviewId == null) {
            return emptyVo(null, null);
        }
        GeoContentReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("审核记录不存在");
        }
        assertCanView(review);
        GeoContentReviewAi row = aiMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewAi>()
                .eq(GeoContentReviewAi::getReviewId, reviewId)
                .last("LIMIT 1"));
        Long writerId = resolveWriterId(review);
        return toVo(row, writerId, review.getPlacementId(), reviewId);
    }

    @Override
    public Map<Long, GeoContentReviewAiVO> mapByReviewIds(Collection<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = reviewIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<GeoContentReviewAi> rows = aiMapper.selectList(new LambdaQueryWrapper<GeoContentReviewAi>()
                .in(GeoContentReviewAi::getReviewId, ids));
        Map<Long, GeoContentReview> reviewMap = reviewMapper.selectBatchIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(GeoContentReview::getId, r -> r, (a, b) -> a));
        Map<Long, Long> writerByPlacement = new HashMap<>();
        List<Long> placementIds = reviewMap.values().stream()
                .map(GeoContentReview::getPlacementId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!placementIds.isEmpty()) {
            placementMapper.selectBatchIds(placementIds).forEach(p -> writerByPlacement.put(p.getId(), p.getOwnerUserId()));
        }
        Map<Long, GeoContentReviewAiVO> map = new HashMap<>();
        for (GeoContentReviewAi row : rows) {
            GeoContentReview review = reviewMap.get(row.getReviewId());
            Long writerId = review == null ? null : writerByPlacement.get(review.getPlacementId());
            map.put(row.getReviewId(), toVo(row, writerId, row.getPlacementId(), row.getReviewId()));
        }
        return map;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GeoContentReviewAiVO rerun(Long reviewId) {
        GeoContentReview review = requirePendingReview(reviewId);
        assertIsReviewer(review);
        GeoContentPlacement placement = requirePlacement(review.getPlacementId());
        if (!reviewRuleService.aiPreReviewEnabled(placement.getOwnerUserId())) {
            throw new BusinessException("该撰写人未开启 AI 预审");
        }
        if (!aiChatService.isEnabled()) {
            throw new BusinessException("尚未配置可用的 AI 模型");
        }
        GeoContentReviewAi row = ensureRow(review, true);
        scheduleRun(row.getId());
        return toVo(row, placement.getOwnerUserId(), placement.getId(), reviewId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<GeoContentReviewAnnotationVO> adopt(Long reviewId) {
        Long uid = requireUserId();
        GeoContentReview review = requirePendingReview(reviewId);
        GeoContentReviewer mine = requirePendingReviewer(review, uid);
        GeoContentReviewAi row = aiMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewAi>()
                .eq(GeoContentReviewAi::getReviewId, reviewId)
                .last("LIMIT 1"));
        if (row == null || !"done".equals(row.getStatus())) {
            throw new BusinessException("AI 预审尚未完成，无法采纳");
        }
        List<GeoContentReviewAiIssueVO> issues = parseIssues(row.getIssuesJson());
        if (issues.isEmpty()) {
            throw new BusinessException("AI 没有给出可采纳的划词建议");
        }
        String authorName = "AI预审";
        List<GeoContentReviewAnnotationVO> created = new ArrayList<>();
        for (GeoContentReviewAiIssueVO issue : issues) {
            String quote = issue.getQuoteText() == null ? "" : issue.getQuoteText().trim();
            String comment = issue.getCommentText() == null ? "" : issue.getCommentText().trim();
            if (!StringUtils.hasText(quote) || !StringUtils.hasText(comment)) {
                continue;
            }
            if (quote.length() > 2000 || comment.length() > 2000) {
                continue;
            }
            GeoContentReviewAnnotation anno = new GeoContentReviewAnnotation();
            anno.setReviewId(review.getId());
            anno.setPlacementId(review.getPlacementId());
            anno.setQuoteText(quote);
            anno.setQuotePrefix(truncateCtx(issue.getQuotePrefix()));
            anno.setQuoteSuffix(truncateCtx(issue.getQuoteSuffix()));
            anno.setCommentText(comment);
            anno.setAuthorUserId(uid);
            anno.setAuthorName(authorName);
            annotationMapper.insert(anno);
            created.add(toAnnotationVo(anno));
        }
        if (created.isEmpty()) {
            throw new BusinessException("划词建议无效，未能写入批注");
        }
        row.setAdopted(1);
        aiMapper.updateById(row);
        // mine 校验已完成，避免未使用告警
        Objects.requireNonNull(mine);
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void feedback(GeoContentReviewAiFeedbackDTO dto) {
        Long uid = requireUserId();
        String fb = dto.getFeedback() == null ? "" : dto.getFeedback().trim().toLowerCase(Locale.ROOT);
        if (!"accurate".equals(fb) && !"inaccurate".equals(fb)) {
            throw new BusinessException("feedback 仅支持 accurate / inaccurate");
        }
        GeoContentReview review = reviewMapper.selectById(dto.getReviewId());
        if (review == null) {
            throw new BusinessException("审核记录不存在");
        }
        assertIsReviewer(review);
        GeoContentReviewAi row = aiMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewAi>()
                .eq(GeoContentReviewAi::getReviewId, dto.getReviewId())
                .last("LIMIT 1"));
        if (row == null || !"done".equals(row.getStatus())) {
            throw new BusinessException("仅可对已完成的 AI 预审反馈");
        }
        row.setFeedback(fb);
        row.setFeedbackRemark(dto.getRemark() == null ? "" : dto.getRemark().trim());
        if (row.getFeedbackRemark().length() > 500) {
            row.setFeedbackRemark(row.getFeedbackRemark().substring(0, 500));
        }
        row.setFeedbackBy(displayName(uid));
        aiMapper.updateById(row);
    }

    @Override
    public int batchEnqueue(GeoContentReviewAiBatchDTO dto) {
        if (dto == null || dto.getReviewIds() == null || dto.getReviewIds().isEmpty()) {
            throw new BusinessException("请选择审核轮次");
        }
        Long uid = requireUserId();
        int n = 0;
        for (Long reviewId : dto.getReviewIds()) {
            if (reviewId == null) {
                continue;
            }
            GeoContentReview review = reviewMapper.selectById(reviewId);
            if (review == null || !"pending".equals(review.getStatus())) {
                continue;
            }
            GeoContentReviewer mine = reviewerMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewer>()
                    .eq(GeoContentReviewer::getReviewId, reviewId)
                    .eq(GeoContentReviewer::getUserId, uid)
                    .last("LIMIT 1"));
            if (mine == null) {
                continue;
            }
            GeoContentPlacement placement = placementMapper.selectById(review.getPlacementId());
            if (placement == null || !reviewRuleService.aiPreReviewEnabled(placement.getOwnerUserId())) {
                continue;
            }
            if (!aiChatService.isEnabled()) {
                continue;
            }
            GeoContentReviewAi existing = aiMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewAi>()
                    .eq(GeoContentReviewAi::getReviewId, reviewId)
                    .last("LIMIT 1"));
            if (existing != null && ("running".equals(existing.getStatus()) || "pending".equals(existing.getStatus()))) {
                continue;
            }
            GeoContentReviewAi row = ensureRow(review, existing != null);
            if (row == null) {
                continue;
            }
            scheduleRun(row.getId());
            n++;
        }
        return n;
    }

    @Override
    public void assertApproveAllowed(Long reviewId, Long writerUserId) {
        if (reviewId == null || writerUserId == null) {
            return;
        }
        if (!reviewRuleService.aiBlockApprove(writerUserId)) {
            return;
        }
        if (!reviewRuleService.aiPreReviewEnabled(writerUserId)) {
            return;
        }
        GeoContentReviewAi row = aiMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewAi>()
                .eq(GeoContentReviewAi::getReviewId, reviewId)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BusinessException("AI 预审尚未开始，按规则需等待完成后再通过");
        }
        String st = row.getStatus();
        if ("pending".equals(st) || "running".equals(st)) {
            throw new BusinessException("AI 预审进行中，按规则需等待完成后再通过（退回不受限）");
        }
        if ("failed".equals(st)) {
            throw new BusinessException("AI 预审失败，请先「重新跑 AI」或联系管理员，按规则不可直接通过");
        }
    }

    private void scheduleRun(Long aiId) {
        if (aiId == null) {
            return;
        }
        Runnable start = () -> submitRun(aiId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    start.run();
                }
            });
        } else {
            start.run();
        }
    }

    private void submitRun(Long aiId) {
        if (aiId == null) {
            return;
        }
        executor.submit(() -> {
            try {
                runAi(aiId);
            } catch (Exception e) {
                log.error("AI 预审异步任务异常 aiId={}", aiId, e);
            }
        });
    }

    private void runAi(Long aiId) {
        GeoContentReviewAi row = aiMapper.selectById(aiId);
        if (row == null) {
            return;
        }
        GeoContentReview review = reviewMapper.selectById(row.getReviewId());
        if (review == null || !"pending".equals(review.getStatus())) {
            row.setStatus("failed");
            row.setErrorMsg("审核轮次已结束");
            row.setFinishedAt(LocalDateTime.now());
            aiMapper.updateById(row);
            return;
        }
        row.setStatus("running");
        row.setStartedAt(LocalDateTime.now());
        row.setErrorMsg("");
        row.setFinishedAt(null);
        aiMapper.updateById(row);

        try {
            String title = nz(review.getContentTitle());
            String body = nz(review.getContentBody());
            if (!StringUtils.hasText(body)) {
                GeoContentPlacement placement = placementMapper.selectById(review.getPlacementId());
                if (placement != null) {
                    title = nz(placement.getContentTitle());
                    body = nz(placement.getContentBody());
                }
            }
            String kb = reviewKbService.buildReviewPromptContext();
            String userPrompt = (StringUtils.hasText(kb) ? "【审稿知识库】\n" + kb + "\n\n" : "")
                    + "【待审文章】\n标题：\n" + (StringUtils.hasText(title) ? title : "（无）")
                    + "\n\n正文：\n" + truncate(body, 12000);
            String raw = aiChatService.chat(SYSTEM_PROMPT, userPrompt, null, 0.2);
            Parsed parsed = parse(raw);

            row.setStatus("done");
            row.setSuggestion(parsed.suggestion());
            row.setScore(parsed.score());
            row.setSummary(parsed.summary());
            row.setIssuesJson(objectMapper.writeValueAsString(parsed.issues()));
            row.setProvider(nz(parsed.provider()));
            row.setModelName(nz(parsed.model()));
            row.setErrorMsg("");
            row.setFinishedAt(LocalDateTime.now());
            row.setAdopted(0);
            row.setFeedback("");
            row.setFeedbackRemark("");
            row.setFeedbackBy("");
            aiMapper.updateById(row);

            maybeAutoSkipStage(review, row);
        } catch (Exception e) {
            log.warn("AI 预审失败 reviewId={}: {}", row.getReviewId(), e.getMessage());
            row.setStatus("failed");
            row.setErrorMsg(truncate(e.getMessage(), 480));
            row.setFinishedAt(LocalDateTime.now());
            aiMapper.updateById(row);
        }
    }

    /** 二期：AI 通过且规则为 skip_stage1_on_pass 时，自动完成本阶段人工 */
    private void maybeAutoSkipStage(GeoContentReview review, GeoContentReviewAi ai) {
        if (review == null || ai == null) {
            return;
        }
        if (!"approve".equals(ai.getSuggestion())) {
            return;
        }
        if (!"pending".equals(review.getStatus())) {
            return;
        }
        GeoContentPlacement placement = placementMapper.selectById(review.getPlacementId());
        if (placement == null) {
            return;
        }
        String mode = reviewRuleService.aiMode(placement.getOwnerUserId());
        if (!"skip_stage1_on_pass".equals(mode)) {
            return;
        }
        int stageNo = review.getStageNo() == null ? Constants.CONTENT_REVIEW_STAGE1 : review.getStageNo();
        // 仅对一审自动跳过人工；二审仍需人审
        if (stageNo != Constants.CONTENT_REVIEW_STAGE1) {
            return;
        }
        try {
            autoApproveStage1(review, placement);
            log.info("AI 预审通过，已按规则跳过一审人工 reviewId={}", review.getId());
        } catch (Exception e) {
            log.warn("AI 跳过一审失败 reviewId={}: {}", review.getId(), e.getMessage());
        }
    }

    private void autoApproveStage1(GeoContentReview review, GeoContentPlacement placement) {
        List<GeoContentReviewer> reviewers = reviewerMapper.selectList(new LambdaQueryWrapper<GeoContentReviewer>()
                .eq(GeoContentReviewer::getReviewId, review.getId()));
        LocalDateTime now = LocalDateTime.now();
        for (GeoContentReviewer r : reviewers) {
            if (!"pending".equals(r.getResult())) {
                continue;
            }
            r.setResult("approved");
            r.setCommentText("AI预审建议通过，按规则跳过一审人工");
            r.setActedAt(now);
            reviewerMapper.updateById(r);
            taskService.completeGeoReviewTask(review.getId(), r.getUserId(),
                    StringUtils.hasText(r.getUserName()) ? r.getUserName() : "系统");
        }
        review.setStatus("approved");
        reviewMapper.updateById(review);
        placement.setContentStatus(Constants.CONTENT_STATUS_STAGE1_PASSED);
        placementMapper.updateById(placement);
        taskService.markGeoWriteStage1Passed(placement.getId());
        taskService.cancelPendingGeoReviewTasks(review.getId(), "AI预审通过，已跳过一审人工");
    }

    private GeoContentReviewAi ensureRow(GeoContentReview review, boolean reset) {
        GeoContentReviewAi existing = aiMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewAi>()
                .eq(GeoContentReviewAi::getReviewId, review.getId())
                .last("LIMIT 1"));
        if (existing == null) {
            GeoContentReviewAi row = new GeoContentReviewAi();
            row.setReviewId(review.getId());
            row.setPlacementId(review.getPlacementId());
            row.setStatus("pending");
            row.setSuggestion("");
            row.setSummary("");
            row.setIssuesJson("[]");
            row.setProvider("");
            row.setModelName("");
            row.setErrorMsg("");
            row.setAdopted(0);
            row.setFeedback("");
            row.setFeedbackRemark("");
            row.setFeedbackBy("");
            aiMapper.insert(row);
            return row;
        }
        if ("running".equals(existing.getStatus()) && !reset) {
            return existing;
        }
        if (reset || "done".equals(existing.getStatus()) || "failed".equals(existing.getStatus())) {
            existing.setStatus("pending");
            existing.setSuggestion("");
            existing.setScore(null);
            existing.setSummary("");
            existing.setIssuesJson("[]");
            existing.setProvider("");
            existing.setModelName("");
            existing.setErrorMsg("");
            existing.setAdopted(0);
            existing.setFeedback("");
            existing.setFeedbackRemark("");
            existing.setFeedbackBy("");
            existing.setStartedAt(null);
            existing.setFinishedAt(null);
            aiMapper.updateById(existing);
        }
        return existing;
    }

    private Parsed parse(String content) throws Exception {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```json", "").replaceFirst("^```", "");
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException("AI 未返回约定 JSON");
        }
        JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
        String suggestion = node.path("suggestion").asText("").trim().toLowerCase(Locale.ROOT);
        if (!"approve".equals(suggestion) && !"reject".equals(suggestion)) {
            throw new BusinessException("AI suggestion 无效");
        }
        int score = node.path("score").asInt(-1);
        if (score < 0 || score > 100) {
            score = "approve".equals(suggestion) ? 80 : 40;
        }
        String summary = node.path("summary").asText("").trim();
        if (summary.length() > 2000) {
            summary = summary.substring(0, 2000);
        }
        List<GeoContentReviewAiIssueVO> issues = new ArrayList<>();
        JsonNode arr = node.path("issues");
        if (arr.isArray()) {
            for (JsonNode item : arr) {
                GeoContentReviewAiIssueVO vo = new GeoContentReviewAiIssueVO();
                vo.setQuoteText(item.path("quoteText").asText("").trim());
                vo.setCommentText(item.path("commentText").asText("").trim());
                String level = item.path("level").asText("mid").trim().toLowerCase(Locale.ROOT);
                if (!"high".equals(level) && !"low".equals(level)) {
                    level = "mid";
                }
                vo.setLevel(level);
                vo.setQuotePrefix(item.path("quotePrefix").asText("").trim());
                vo.setQuoteSuffix(item.path("quoteSuffix").asText("").trim());
                if (StringUtils.hasText(vo.getQuoteText()) && StringUtils.hasText(vo.getCommentText())) {
                    issues.add(vo);
                }
            }
        }
        if (issues.size() > 30) {
            issues = issues.subList(0, 30);
        }
        return new Parsed(suggestion, score, summary, issues, "", "");
    }

    private List<GeoContentReviewAiIssueVO> parseIssues(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<GeoContentReviewAiIssueVO>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private GeoContentReviewAiVO toVo(GeoContentReviewAi row, Long writerUserId, Long placementId, Long reviewId) {
        if (row == null) {
            return emptyVo(writerUserId, reviewId != null ? reviewId : null, placementId);
        }
        GeoContentReviewAiVO vo = new GeoContentReviewAiVO();
        vo.setId(row.getId());
        vo.setReviewId(row.getReviewId());
        vo.setPlacementId(row.getPlacementId());
        vo.setStatus(row.getStatus());
        vo.setSuggestion(nz(row.getSuggestion()));
        vo.setScore(row.getScore());
        vo.setSummary(nz(row.getSummary()));
        vo.setIssues(parseIssues(row.getIssuesJson()));
        vo.setProvider(nz(row.getProvider()));
        vo.setModelName(nz(row.getModelName()));
        vo.setErrorMsg(nz(row.getErrorMsg()));
        vo.setAdopted(row.getAdopted() != null && row.getAdopted() != 0);
        vo.setFeedback(nz(row.getFeedback()));
        vo.setFeedbackRemark(nz(row.getFeedbackRemark()));
        vo.setStartedAt(row.getStartedAt());
        vo.setFinishedAt(row.getFinishedAt());
        fillRuleMeta(vo, writerUserId);
        return vo;
    }

    private GeoContentReviewAiVO emptyVo(Long writerUserId, Long reviewId) {
        return emptyVo(writerUserId, reviewId, null);
    }

    private GeoContentReviewAiVO emptyVo(Long writerUserId, Long reviewId, Long placementId) {
        GeoContentReviewAiVO vo = new GeoContentReviewAiVO();
        vo.setReviewId(reviewId);
        vo.setPlacementId(placementId);
        vo.setStatus("none");
        vo.setSuggestion("");
        vo.setSummary("");
        vo.setIssues(List.of());
        vo.setAdopted(false);
        fillRuleMeta(vo, writerUserId);
        return vo;
    }

    private void fillRuleMeta(GeoContentReviewAiVO vo, Long writerUserId) {
        boolean enabled = writerUserId == null || reviewRuleService.aiPreReviewEnabled(writerUserId);
        vo.setAiPreReview(enabled);
        vo.setAiMode(writerUserId == null ? "assist" : reviewRuleService.aiMode(writerUserId));
        vo.setAiBlockApprove(writerUserId != null && reviewRuleService.aiBlockApprove(writerUserId));
    }

    private Long resolveWriterId(GeoContentReview review) {
        if (review == null || review.getPlacementId() == null) {
            return null;
        }
        GeoContentPlacement p = placementMapper.selectById(review.getPlacementId());
        return p == null ? null : p.getOwnerUserId();
    }

    private void assertCanView(GeoContentReview review) {
        Long uid = requireUserId();
        if (Objects.equals(review.getSubmitterUserId(), uid)) {
            return;
        }
        GeoContentPlacement placement = placementMapper.selectById(review.getPlacementId());
        if (placement != null && Objects.equals(placement.getOwnerUserId(), uid)) {
            return;
        }
        Long asReviewer = reviewerMapper.selectCount(new LambdaQueryWrapper<GeoContentReviewer>()
                .eq(GeoContentReviewer::getReviewId, review.getId())
                .eq(GeoContentReviewer::getUserId, uid));
        if (asReviewer != null && asReviewer > 0) {
            return;
        }
        throw new BusinessException("无权查看该审核的 AI 预审");
    }

    private void assertIsReviewer(GeoContentReview review) {
        Long uid = requireUserId();
        GeoContentReviewer mine = reviewerMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewer>()
                .eq(GeoContentReviewer::getReviewId, review.getId())
                .eq(GeoContentReviewer::getUserId, uid)
                .last("LIMIT 1"));
        if (mine == null) {
            throw new BusinessException("你不是本轮审核人");
        }
    }

    private GeoContentReview requirePendingReview(Long reviewId) {
        if (reviewId == null) {
            throw new BusinessException("审核轮次ID不能为空");
        }
        GeoContentReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("审核记录不存在");
        }
        if (!"pending".equals(review.getStatus())) {
            throw new BusinessException("该轮审核已结束");
        }
        return review;
    }

    private GeoContentReviewer requirePendingReviewer(GeoContentReview review, Long uid) {
        GeoContentReviewer mine = reviewerMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewer>()
                .eq(GeoContentReviewer::getReviewId, review.getId())
                .eq(GeoContentReviewer::getUserId, uid)
                .last("LIMIT 1"));
        if (mine == null) {
            throw new BusinessException("你不是本轮审核人");
        }
        if (!"pending".equals(mine.getResult())) {
            throw new BusinessException("你已提交过审核意见");
        }
        return mine;
    }

    private GeoContentPlacement requirePlacement(Long id) {
        GeoContentPlacement p = placementMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("内容投放不存在");
        }
        return p;
    }

    private Long requireUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("未登录");
        }
        return uid;
    }

    private String displayName(Long userId) {
        return SecurityUtils.getCurrentUsername();
    }

    private GeoContentReviewAnnotationVO toAnnotationVo(GeoContentReviewAnnotation row) {
        GeoContentReviewAnnotationVO vo = new GeoContentReviewAnnotationVO();
        vo.setId(row.getId());
        vo.setReviewId(row.getReviewId());
        vo.setPlacementId(row.getPlacementId());
        vo.setQuoteText(nz(row.getQuoteText()));
        vo.setQuotePrefix(nz(row.getQuotePrefix()));
        vo.setQuoteSuffix(nz(row.getQuoteSuffix()));
        vo.setStartOffset(row.getStartOffset());
        vo.setEndOffset(row.getEndOffset());
        vo.setCommentText(nz(row.getCommentText()));
        vo.setAuthorUserId(row.getAuthorUserId());
        vo.setAuthorName(nz(row.getAuthorName()));
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    private static String truncateCtx(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() <= 200) {
            return text;
        }
        return text.substring(0, 200);
    }

    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private record Parsed(
            String suggestion,
            int score,
            String summary,
            List<GeoContentReviewAiIssueVO> issues,
            String provider,
            String model) {
    }
}
