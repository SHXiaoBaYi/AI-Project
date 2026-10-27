package com.base.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.exception.BusinessException;
import com.base.admin.domain.dto.GeoContentReviewAnnotationSaveDTO;
import com.base.admin.domain.dto.GeoContentReviewAnnotationUpdateDTO;
import com.base.admin.domain.dto.GeoContentReviewDecideDTO;
import com.base.admin.domain.dto.GeoContentReviewQueryDTO;
import com.base.admin.domain.dto.GeoContentSubmitReviewDTO;
import com.base.admin.domain.dto.GeoContentWritingQueryDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoContentReview;
import com.base.admin.domain.entity.GeoContentReviewAnnotation;
import com.base.admin.domain.entity.GeoContentReviewer;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.domain.vo.GeoContentReviewAiVO;
import com.base.admin.domain.vo.GeoContentReviewAnnotationVO;
import com.base.admin.domain.vo.GeoContentReviewCompareVO;
import com.base.admin.domain.vo.GeoContentReviewRoundVO;
import com.base.admin.domain.vo.GeoContentReviewRowVO;
import com.base.admin.domain.vo.GeoContentReviewerItemVO;
import com.base.admin.domain.vo.GeoContentWritingRowVO;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.GeoContentReviewAnnotationMapper;
import com.base.admin.mapper.GeoContentReviewMapper;
import com.base.admin.mapper.GeoContentReviewerMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.mapper.SysUserMapper;
import com.base.admin.service.GeoContentPlacementService;
import com.base.admin.service.GeoContentReviewAiService;
import com.base.admin.service.GeoContentReviewRuleService;
import com.base.admin.service.GeoContentWriteService;
import com.base.admin.service.SysTaskService;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeoContentWriteServiceImpl implements GeoContentWriteService {

    private final GeoContentPlacementMapper placementMapper;
    private final GeoContentReviewMapper reviewMapper;
    private final GeoContentReviewerMapper reviewerMapper;
    private final GeoContentReviewAnnotationMapper annotationMapper;
    private final SysUserMapper userMapper;
    private final SysTaskMapper taskMapper;
    private final SysTaskService taskService;
    private final GeoContentPlacementService placementService;
    private final GeoContentReviewRuleService reviewRuleService;
    private final GeoContentReviewAiService reviewAiService;

    @Override
    public List<GeoContentWritingRowVO> listMyWritings(GeoContentWritingQueryDTO query) {
        Long uid = requireUserId();
        Set<Long> writeTaskPlacementIds = taskMapper.selectList(new LambdaQueryWrapper<SysTask>()
                        .eq(SysTask::getBizType, Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT)
                        .eq(SysTask::getTaskType, Constants.TASK_TYPE_GEO_WRITE)
                        .eq(SysTask::getOwnerUserId, uid)
                        .ne(SysTask::getStatus, "已取消")
                        .isNotNull(SysTask::getBizId)
                        .and(t -> t.ge(SysTask::getCreateTime, Constants.GEO_ONLINE_WRITE_LAUNCH_AT)
                                .or()
                                .ge(SysTask::getProgress, Constants.TASK_PROGRESS_WRITE_DRAFTED))
                        .select(SysTask::getBizId))
                .stream()
                .map(SysTask::getBizId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LambdaQueryWrapper<GeoContentPlacement> qw = new LambdaQueryWrapper<GeoContentPlacement>()
                // 撰写人=当前用户，或上线后/已动笔的撰写任务挂在该投放上
                .and(w -> {
                    w.eq(GeoContentPlacement::getOwnerUserId, uid)
                            .and(inner -> inner.ge(GeoContentPlacement::getCreateTime, Constants.GEO_ONLINE_WRITE_LAUNCH_AT)
                                    .or()
                                    .ge(GeoContentPlacement::getContentUpdatedAt, Constants.GEO_ONLINE_WRITE_LAUNCH_AT)
                                    .or(!writeTaskPlacementIds.isEmpty(),
                                            inner2 -> inner2.in(GeoContentPlacement::getId, writeTaskPlacementIds)));
                    if (!writeTaskPlacementIds.isEmpty()) {
                        w.or().in(GeoContentPlacement::getId, writeTaskPlacementIds);
                    }
                });
        if (query != null) {
            if (query.getTopicId() != null) {
                qw.eq(GeoContentPlacement::getTopicId, query.getTopicId());
            }
            if (StringUtils.hasText(query.getTargetQuestion())) {
                qw.like(GeoContentPlacement::getTargetQuestion, query.getTargetQuestion().trim());
            }
            if (StringUtils.hasText(query.getContentTitle())) {
                qw.like(GeoContentPlacement::getContentTitle, query.getContentTitle().trim());
            }
            if (StringUtils.hasText(query.getContentStatus())) {
                qw.eq(GeoContentPlacement::getContentStatus, query.getContentStatus().trim());
            }
        }
        qw.orderByDesc(GeoContentPlacement::getContentUpdatedAt).orderByDesc(GeoContentPlacement::getId);
        List<GeoContentPlacement> rows = placementMapper.selectList(qw);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> placementIds = rows.stream().map(GeoContentPlacement::getId).toList();
        List<GeoContentReview> allReviews = reviewMapper.selectList(new LambdaQueryWrapper<GeoContentReview>()
                .in(GeoContentReview::getPlacementId, placementIds)
                .orderByAsc(GeoContentReview::getId));
        Map<Long, List<GeoContentReview>> reviewsByPlacement = allReviews.stream()
                .collect(Collectors.groupingBy(GeoContentReview::getPlacementId));
        Map<Long, GeoContentReview> pendingByPlacement = latestPendingByPlacement(placementIds);
        Map<Long, GeoContentReview> latestByPlacement = latestReviewByPlacement(placementIds);
        Map<Long, List<GeoContentReviewer>> reviewersByReview = loadReviewersByReviewIds(
                allReviews.stream().map(GeoContentReview::getId).toList());
        Map<Long, GeoContentReviewAiVO> aiByReview = reviewAiService.mapByReviewIds(
                allReviews.stream().map(GeoContentReview::getId).toList());
        Set<Long> skipStage1Writers = reviewRuleService.writerIdsSkippingStage1(
                rows.stream().map(GeoContentPlacement::getOwnerUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<GeoContentWritingRowVO> result = new ArrayList<>();
        for (GeoContentPlacement p : rows) {
            GeoContentWritingRowVO vo = new GeoContentWritingRowVO();
            vo.setPlacementId(p.getId());
            vo.setTopicName(nz(p.getTopicName()));
            vo.setTargetQuestion(nz(p.getTargetQuestion()));
            vo.setContentTitle(nz(p.getContentTitle()));
            vo.setContentStatus(StringUtils.hasText(p.getContentStatus())
                    ? p.getContentStatus()
                    : Constants.CONTENT_STATUS_DRAFTING);
            vo.setOwnerName(nz(p.getOwnerName()));
            vo.setContentVersion(p.getContentVersion() == null ? 0L : p.getContentVersion());
            vo.setContentUpdatedAt(p.getContentUpdatedAt());
            vo.setCreateTime(p.getCreateTime());
            List<GeoContentReview> history = reviewsByPlacement.getOrDefault(p.getId(), List.of());
            boolean skipStage1 = skipStage1Writers.contains(p.getOwnerUserId());
            vo.setSkipStage1(skipStage1 ? 1 : 0);
            int nextStage = nextReviewStage(history, skipStage1);
            if (Constants.CONTENT_STATUS_QUEUE.equals(vo.getContentStatus())
                    || Constants.CONTENT_STATUS_PUBLISHED.equals(vo.getContentStatus())
                    || Constants.CONTENT_STATUS_PENDING_REVIEW.equals(vo.getContentStatus())) {
                nextStage = 0;
            }
            vo.setNextStageNo(nextStage > 0 ? nextStage : null);
            GeoContentReviewer stage1Reviewer = firstReviewerOfStage(history, Constants.CONTENT_REVIEW_STAGE1, reviewersByReview);
            if (stage1Reviewer != null) {
                vo.setStage1ReviewerUserId(stage1Reviewer.getUserId());
                vo.setStage1ReviewerName(nz(stage1Reviewer.getUserName()));
            }
            if (nextStage > 0) {
                GeoContentReviewer locked = lockedReviewer(history, nextStage, reviewersByReview);
                if (locked != null) {
                    vo.setLockedReviewerUserId(locked.getUserId());
                    vo.setLockedReviewerName(nz(locked.getUserName()));
                }
            }
            GeoContentReview pending = pendingByPlacement.get(p.getId());
            if (pending != null) {
                vo.setCurrentReviewId(pending.getId());
                vo.setCurrentRoundNo(pending.getRoundNo());
                vo.setCurrentStageNo(reviewStageOf(pending));
            }
            GeoContentReview latest = latestByPlacement.get(p.getId());
            if (latest != null) {
                vo.setLatestReviewId(latest.getId());
                vo.setLatestReviewStatus(latest.getStatus());
                vo.setLatestRoundNo(latest.getRoundNo());
                vo.setLatestStageNo(reviewStageOf(latest));
                List<GeoContentReviewer> reviewers = reviewersByReview.getOrDefault(latest.getId(), List.of());
                List<GeoContentReviewerItemVO> items = new ArrayList<>();
                StringBuilder summary = new StringBuilder();
                StringBuilder reject = new StringBuilder();
                for (GeoContentReviewer r : reviewers) {
                    GeoContentReviewerItemVO item = new GeoContentReviewerItemVO();
                    item.setUserId(r.getUserId());
                    item.setUserName(nz(r.getUserName()));
                    item.setResult(r.getResult());
                    item.setComment(nz(r.getCommentText()));
                    item.setActedAt(r.getActedAt());
                    items.add(item);
                    if (summary.length() > 0) {
                        summary.append(' ');
                    }
                    summary.append(nz(r.getUserName())).append(resultLabel(r.getResult()));
                    if ("rejected".equals(r.getResult()) && StringUtils.hasText(r.getCommentText())) {
                        if (reject.length() > 0) {
                            reject.append('；');
                        }
                        reject.append(nz(r.getUserName())).append('：').append(r.getCommentText().trim());
                    }
                }
                List<GeoContentReviewAnnotation> latestAnnos = annotationMapper.selectList(
                        new LambdaQueryWrapper<GeoContentReviewAnnotation>()
                                .eq(GeoContentReviewAnnotation::getReviewId, latest.getId())
                                .orderByAsc(GeoContentReviewAnnotation::getId)
                                .last("LIMIT 5"));
                if (!latestAnnos.isEmpty()) {
                    if (reject.length() > 0) {
                        reject.append('；');
                    }
                    reject.append("划词批注").append(latestAnnos.size()).append("处");
                    for (GeoContentReviewAnnotation a : latestAnnos) {
                        reject.append('；').append('「')
                                .append(shortQuote(a.getQuoteText()))
                                .append('」')
                                .append(nz(a.getCommentText()));
                    }
                }
                vo.setReviewers(items);
                vo.setReviewerSummary(summary.toString());
                vo.setRejectSummary(reject.toString());
            }
            Long aiReviewId = pending != null ? pending.getId() : (latest != null ? latest.getId() : null);
            if (aiReviewId != null) {
                vo.setAi(aiByReview.get(aiReviewId));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<GeoContentReviewRowVO> listPendingReviews() {
        Long uid = requireUserId();
        List<GeoContentReviewer> mine = reviewerMapper.selectList(new LambdaQueryWrapper<GeoContentReviewer>()
                .eq(GeoContentReviewer::getUserId, uid)
                .eq(GeoContentReviewer::getResult, "pending")
                .orderByDesc(GeoContentReviewer::getId));
        if (mine.isEmpty()) {
            return List.of();
        }
        List<Long> reviewIds = mine.stream().map(GeoContentReviewer::getReviewId).distinct().toList();
        Map<Long, GeoContentReview> reviewMap = loadReviews(reviewIds).stream()
                .filter(r -> "pending".equals(r.getStatus()))
                .collect(Collectors.toMap(GeoContentReview::getId, r -> r, (a, b) -> a));
        if (reviewMap.isEmpty()) {
            return List.of();
        }
        return buildReviewRows(new ArrayList<>(reviewMap.values()), uid);
    }

    @Override
    public List<GeoContentReviewRowVO> listReviewRecords(GeoContentReviewQueryDTO query) {
        Long uid = requireUserId();
        Set<Long> candidateIds = new LinkedHashSet<>();
        List<GeoContentReview> submitted = reviewMapper.selectList(new LambdaQueryWrapper<GeoContentReview>()
                .eq(GeoContentReview::getSubmitterUserId, uid)
                .orderByDesc(GeoContentReview::getId)
                .last("LIMIT 500"));
        submitted.forEach(r -> candidateIds.add(r.getId()));
        List<GeoContentReviewer> participated = reviewerMapper.selectList(new LambdaQueryWrapper<GeoContentReviewer>()
                .eq(GeoContentReviewer::getUserId, uid)
                .orderByDesc(GeoContentReviewer::getId)
                .last("LIMIT 500"));
        participated.forEach(r -> candidateIds.add(r.getReviewId()));
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Set<Long> reviewIds = candidateIds;
        if (query != null && query.getReviewerUserId() != null) {
            List<Long> matched = reviewerMapper.selectList(new LambdaQueryWrapper<GeoContentReviewer>()
                            .in(GeoContentReviewer::getReviewId, candidateIds)
                            .eq(GeoContentReviewer::getUserId, query.getReviewerUserId()))
                    .stream()
                    .map(GeoContentReviewer::getReviewId)
                    .distinct()
                    .toList();
            reviewIds = new LinkedHashSet<>(matched);
            if (reviewIds.isEmpty()) {
                return List.of();
            }
        }

        LambdaQueryWrapper<GeoContentReview> qw = new LambdaQueryWrapper<GeoContentReview>()
                .in(GeoContentReview::getId, reviewIds);
        if (query != null) {
            if (StringUtils.hasText(query.getStatus())) {
                qw.eq(GeoContentReview::getStatus, query.getStatus().trim());
            }
            if (query.getSubmitterUserId() != null) {
                qw.eq(GeoContentReview::getSubmitterUserId, query.getSubmitterUserId());
            }
        }
        qw.orderByDesc(GeoContentReview::getId);
        List<GeoContentReview> reviews = reviewMapper.selectList(qw);
        if (reviews.isEmpty()) {
            return List.of();
        }

        boolean needPlacementFilter = query != null
                && (query.getTopicId() != null
                        || StringUtils.hasText(query.getTargetQuestion())
                        || StringUtils.hasText(query.getContentTitle()));
        if (needPlacementFilter) {
            List<Long> placementIds = reviews.stream().map(GeoContentReview::getPlacementId).distinct().toList();
            Map<Long, GeoContentPlacement> placementMap = placementMapper.selectBatchIds(placementIds).stream()
                    .collect(Collectors.toMap(GeoContentPlacement::getId, p -> p, (a, b) -> a));
            String titleKw = query.getContentTitle() == null ? "" : query.getContentTitle().trim();
            String questionKw = query.getTargetQuestion() == null ? "" : query.getTargetQuestion().trim();
            Long topicId = query.getTopicId();
            reviews = reviews.stream().filter(r -> {
                GeoContentPlacement p = placementMap.get(r.getPlacementId());
                if (topicId != null && (p == null || !Objects.equals(p.getTopicId(), topicId))) {
                    return false;
                }
                if (StringUtils.hasText(questionKw)) {
                    String q = p == null ? "" : nz(p.getTargetQuestion());
                    if (!q.contains(questionKw)) {
                        return false;
                    }
                }
                if (StringUtils.hasText(titleKw)) {
                    String reviewTitle = nz(r.getContentTitle());
                    String placementTitle = p == null ? "" : nz(p.getContentTitle());
                    if (!reviewTitle.contains(titleKw) && !placementTitle.contains(titleKw)) {
                        return false;
                    }
                }
                return true;
            }).toList();
            if (reviews.isEmpty()) {
                return List.of();
            }
        }

        return buildReviewRows(reviews, uid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GeoContentReviewCompareVO getReviewCompare(Long placementId) {
        Long uid = requireUserId();
        GeoContentPlacement placement = requirePlacement(placementId);
        assertCanViewReviewHistory(placement, uid);

        List<GeoContentReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<GeoContentReview>()
                .eq(GeoContentReview::getPlacementId, placementId)
                .orderByAsc(GeoContentReview::getRoundNo)
                .orderByAsc(GeoContentReview::getId));
        // 补齐历史轮次缺失的文章正文快照（提审时未落库的旧数据 / 待审轮）
        backfillMissingArticleSnapshots(placement, reviews);

        Map<Long, List<GeoContentReviewer>> reviewersByReview = loadReviewersByReviewIds(
                reviews.stream().map(GeoContentReview::getId).toList());
        Map<Long, List<GeoContentReviewAnnotation>> annotationsByReview = loadAnnotationsByReviewIds(
                reviews.stream().map(GeoContentReview::getId).toList());
        Map<Long, GeoContentReviewAiVO> aiByReview = reviewAiService.mapByReviewIds(
                reviews.stream().map(GeoContentReview::getId).toList());

        Long currentReviewId = null;
        List<GeoContentReviewRoundVO> rounds = new ArrayList<>();
        for (GeoContentReview review : reviews) {
            if ("pending".equals(review.getStatus())) {
                currentReviewId = review.getId();
            }
            GeoContentReviewRoundVO roundVo = toRoundVo(
                    review,
                    reviewersByReview.getOrDefault(review.getId(), List.of()),
                    annotationsByReview.getOrDefault(review.getId(), List.of()));
            roundVo.setAi(aiByReview.get(review.getId()));
            rounds.add(roundVo);
        }

        GeoContentReviewCompareVO vo = new GeoContentReviewCompareVO();
        vo.setPlacementId(placement.getId());
        vo.setTopicName(nz(placement.getTopicName()));
        vo.setTargetQuestion(nz(placement.getTargetQuestion()));
        vo.setCurrentReviewId(currentReviewId);
        vo.setCurrentDraft(toCurrentDraftVo(placement));
        vo.setRounds(rounds);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitReview(GeoContentSubmitReviewDTO dto) {
        Long uid = requireUserId();
        GeoContentPlacement placement = requirePlacement(dto.getPlacementId());
        if (!Objects.equals(placement.getOwnerUserId(), uid)) {
            throw new BusinessException("仅文章撰写人可发起审核");
        }
        String status = placement.getContentStatus();
        if (Constants.CONTENT_STATUS_PENDING_REVIEW.equals(status)) {
            throw new BusinessException("已有进行中的审核，请等待审核完成或退回后再发起");
        }
        if (Constants.CONTENT_STATUS_QUEUE.equals(status) || Constants.CONTENT_STATUS_PUBLISHED.equals(status)) {
            throw new BusinessException("待投放/已发布文章不可再发起审核，请到「投放管理」维护投放");
        }
        String title = nz(placement.getContentTitle());
        String body = nz(placement.getContentBody());
        if (!StringUtils.hasText(title) && !StringUtils.hasText(body)) {
            throw new BusinessException("请先撰写并保存文章内容再发起审核");
        }
        List<GeoContentReview> history = reviewMapper.selectList(new LambdaQueryWrapper<GeoContentReview>()
                .eq(GeoContentReview::getPlacementId, placement.getId())
                .orderByAsc(GeoContentReview::getId));
        boolean skipStage1 = reviewRuleService.skipStage1(placement.getOwnerUserId());
        int stageNo = nextReviewStage(history, skipStage1);
        if (stageNo == 0) {
            throw new BusinessException("当前不能发起审核");
        }
        if (stageNo < 0) {
            throw new BusinessException("两阶段审核均已通过，请到「投放管理」维护投放");
        }
        Map<Long, List<GeoContentReviewer>> reviewersByReview = loadReviewersByReviewIds(
                history.stream().map(GeoContentReview::getId).toList());
        GeoContentReviewer locked = lockedReviewer(history, stageNo, reviewersByReview);
        Long reviewerId;
        if (locked != null && locked.getUserId() != null) {
            reviewerId = locked.getUserId();
        } else {
            reviewerId = resolveReviewerUserId(dto);
            if (reviewerId == null) {
                throw new BusinessException(stageNo == Constants.CONTENT_REVIEW_STAGE2 ? "请选择二审审核人" : "请选择一审审核人");
            }
        }
        if (Objects.equals(reviewerId, uid)) {
            throw new BusinessException("不能将自己设为审核人");
        }
        if (stageNo == Constants.CONTENT_REVIEW_STAGE2) {
            GeoContentReviewer stage1 = firstReviewerOfStage(history, Constants.CONTENT_REVIEW_STAGE1, reviewersByReview);
            if (stage1 != null && Objects.equals(reviewerId, stage1.getUserId())) {
                throw new BusinessException("二审审核人不能与一审是同一人");
            }
        }
        Map<Long, SysUser> users = loadUsers(List.of(reviewerId));
        SysUser reviewer = users.get(reviewerId);
        if (reviewer == null) {
            throw new BusinessException("审核人不存在：" + reviewerId);
        }

        String submitterName = StringUtils.hasText(placement.getOwnerName())
                ? placement.getOwnerName().trim()
                : displayName(uid);
        String remark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        String reviewerName = locked != null && StringUtils.hasText(locked.getUserName())
                ? locked.getUserName().trim()
                : displayName(reviewer);

        // 同阶段被驳回后再提审：复用原审核记录并追加审核人明细，避免重复生成审核任务
        GeoContentReview lastSameStage = lastOfStage(history, stageNo);
        GeoContentReview review;
        if (lastSameStage != null && "rejected".equals(lastSameStage.getStatus())) {
            review = lastSameStage;
            review.setStatus("pending");
            review.setSubmitterUserId(uid);
            review.setSubmitterName(submitterName);
            review.setSubmitRemark(remark);
            applyArticleSnapshot(review, placement);
            reviewMapper.updateById(review);
            ensureArticleSnapshotPersisted(review.getId(), placement);

            GeoContentReviewer row = new GeoContentReviewer();
            row.setReviewId(review.getId());
            row.setPlacementId(placement.getId());
            row.setUserId(reviewerId);
            row.setUserName(reviewerName);
            row.setResult("pending");
            row.setCommentText("");
            reviewerMapper.insert(row);
        } else {
            Integer maxRound = history.stream()
                    .map(GeoContentReview::getRoundNo)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(0);
            int roundNo = maxRound + 1;

            review = new GeoContentReview();
            review.setPlacementId(placement.getId());
            review.setRoundNo(roundNo);
            review.setStageNo(stageNo);
            review.setStatus("pending");
            review.setSubmitterUserId(uid);
            review.setSubmitterName(submitterName);
            review.setSubmitRemark(remark);
            applyArticleSnapshot(review, placement);
            reviewMapper.insert(review);
            ensureArticleSnapshotPersisted(review.getId(), placement);

            GeoContentReviewer row = new GeoContentReviewer();
            row.setReviewId(review.getId());
            row.setPlacementId(placement.getId());
            row.setUserId(reviewerId);
            row.setUserName(reviewerName);
            row.setResult("pending");
            row.setCommentText("");
            reviewerMapper.insert(row);
        }

        placement.setContentStatus(Constants.CONTENT_STATUS_PENDING_REVIEW);
        placementMapper.updateById(placement);
        if (stageNo == Constants.CONTENT_REVIEW_STAGE2 && skipStage1
                && lastOfStage(history, Constants.CONTENT_REVIEW_STAGE1) == null) {
            taskService.markGeoWriteStage1Passed(placement.getId());
        }

        String bizTitle = StringUtils.hasText(placement.getContentTitle())
                ? placement.getContentTitle().trim()
                : nz(placement.getTargetQuestion());
        taskService.createGeoReviewTasks(review.getId(), placement.getId(), bizTitle, List.of(reviewerId), stageNo,
                placement.getOwnerUserId());
        reviewAiService.enqueueAfterSubmit(review.getId());
        return review.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decide(GeoContentReviewDecideDTO dto) {
        Long uid = requireUserId();
        String action = dto.getAction() == null ? "" : dto.getAction().trim().toLowerCase();
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new BusinessException("action 仅支持 approve / reject");
        }
        GeoContentReview review = reviewMapper.selectById(dto.getReviewId());
        if (review == null) {
            throw new BusinessException("审核记录不存在");
        }
        if (!"pending".equals(review.getStatus())) {
            throw new BusinessException("该轮审核已结束");
        }
        GeoContentReviewer mine = requirePendingReviewer(review.getId(), uid);
        String comment = dto.getComment() == null ? "" : dto.getComment().trim();
        long annotationCount = annotationMapper.selectCount(new LambdaQueryWrapper<GeoContentReviewAnnotation>()
                .eq(GeoContentReviewAnnotation::getReviewId, review.getId())
                .eq(GeoContentReviewAnnotation::getAuthorUserId, uid));
        if ("reject".equals(action) && !StringUtils.hasText(comment) && annotationCount <= 0) {
            throw new BusinessException("退回时请填写审核意见，或先划词批注");
        }
        if ("reject".equals(action) && !StringUtils.hasText(comment) && annotationCount > 0) {
            comment = "已划词批注 " + annotationCount + " 处，请按批注修改";
        }
        if ("approve".equals(action)) {
            GeoContentPlacement forRule = requirePlacement(review.getPlacementId());
            reviewAiService.assertApproveAllowed(review.getId(), forRule.getOwnerUserId());
        }

        mine.setResult("approve".equals(action) ? "approved" : "rejected");
        mine.setCommentText(comment);
        mine.setActedAt(LocalDateTime.now());
        reviewerMapper.updateById(mine);

        GeoContentPlacement placement = requirePlacement(review.getPlacementId());
        // 审核决定前若缺文章快照，先按当前正文补上（此时稿面应仍是提审稿）
        fillArticleSnapshotIfMissing(review, placement);

        taskService.completeGeoReviewTask(review.getId(), uid, displayName(uid));

        if ("reject".equals(action)) {
            review.setStatus("rejected");
            reviewMapper.updateById(review);
            placement.setContentStatus(Constants.CONTENT_STATUS_REVISING);
            placementMapper.updateById(placement);
            taskService.cancelPendingGeoReviewTasks(review.getId(), "文章已退回修改，取消其余审核任务");
            return;
        }

        List<GeoContentReviewer> latestPerUser = latestReviewersOf(review.getId());
        boolean pendingLeft = latestPerUser.stream().anyMatch(r -> "pending".equals(r.getResult()));
        if (pendingLeft) {
            return;
        }
        boolean rejected = latestPerUser.stream().anyMatch(r -> "rejected".equals(r.getResult()));
        if (rejected) {
            review.setStatus("rejected");
            reviewMapper.updateById(review);
            placement.setContentStatus(Constants.CONTENT_STATUS_REVISING);
            placementMapper.updateById(placement);
            taskService.cancelPendingGeoReviewTasks(review.getId(), "本轮审核未通过，取消其余审核任务");
            return;
        }

        review.setStatus("approved");
        reviewMapper.updateById(review);
        int stageNo = reviewStageOf(review);
        if (stageNo == Constants.CONTENT_REVIEW_STAGE1) {
            placement.setContentStatus(Constants.CONTENT_STATUS_STAGE1_PASSED);
            placementMapper.updateById(placement);
            taskService.markGeoWriteStage1Passed(placement.getId());
            taskService.cancelPendingGeoReviewTasks(review.getId(), "一审已通过");
            return;
        }
        placement.setContentStatus(Constants.CONTENT_STATUS_QUEUE);
        placement.setPlacementProgress(Constants.CONTENT_AGG_PENDING);
        placementMapper.updateById(placement);
        placementService.ensureQueueArticleItems(placement.getId());

        Long writerId = placement.getOwnerUserId();
        String writerName = StringUtils.hasText(placement.getOwnerName())
                ? placement.getOwnerName().trim()
                : displayName(writerId);
        taskService.completeGeoWriteTasksByReview(placement.getId(), writerId, writerName);
        taskService.cancelPendingGeoReviewTasks(review.getId(), "二审已通过");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GeoContentReviewAnnotationVO addAnnotation(GeoContentReviewAnnotationSaveDTO dto) {
        Long uid = requireUserId();
        GeoContentReview review = reviewMapper.selectById(dto.getReviewId());
        if (review == null) {
            throw new BusinessException("审核记录不存在");
        }
        if (!"pending".equals(review.getStatus())) {
            throw new BusinessException("该轮审核已结束，不能再批注");
        }
        requirePendingReviewer(review.getId(), uid);
        String quote = dto.getQuoteText() == null ? "" : dto.getQuoteText().trim();
        String comment = dto.getCommentText() == null ? "" : dto.getCommentText().trim();
        if (!StringUtils.hasText(quote)) {
            throw new BusinessException("请先划选正文");
        }
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException("请填写批注内容");
        }
        if (quote.length() > 2000) {
            throw new BusinessException("划选原文不能超过 2000 字");
        }
        if (comment.length() > 2000) {
            throw new BusinessException("批注不能超过 2000 字");
        }
        String prefix = truncateCtx(dto.getQuotePrefix());
        String suffix = truncateCtx(dto.getQuoteSuffix());
        Integer start = dto.getStartOffset();
        Integer end = dto.getEndOffset();
        if (start != null && end != null && (start < 0 || end < start)) {
            throw new BusinessException("选区偏移无效");
        }

        GeoContentReviewAnnotation row = new GeoContentReviewAnnotation();
        row.setReviewId(review.getId());
        row.setPlacementId(review.getPlacementId());
        row.setQuoteText(quote);
        row.setQuotePrefix(prefix);
        row.setQuoteSuffix(suffix);
        row.setStartOffset(start);
        row.setEndOffset(end);
        row.setCommentText(comment);
        row.setAuthorUserId(uid);
        row.setAuthorName(displayName(uid));
        annotationMapper.insert(row);
        return toAnnotationVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GeoContentReviewAnnotationVO updateAnnotation(GeoContentReviewAnnotationUpdateDTO dto) {
        Long uid = requireUserId();
        if (dto.getId() == null) {
            throw new BusinessException("批注ID不能为空");
        }
        GeoContentReviewAnnotation row = annotationMapper.selectById(dto.getId());
        if (row == null) {
            throw new BusinessException("批注不存在");
        }
        if (!Objects.equals(row.getAuthorUserId(), uid)) {
            throw new BusinessException("只能修改自己的批注");
        }
        GeoContentReview review = reviewMapper.selectById(row.getReviewId());
        if (review == null || !"pending".equals(review.getStatus())) {
            throw new BusinessException("该轮审核已结束，不能修改批注");
        }
        requirePendingReviewer(review.getId(), uid);
        String comment = dto.getCommentText() == null ? "" : dto.getCommentText().trim();
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException("请填写批注内容");
        }
        if (comment.length() > 2000) {
            throw new BusinessException("批注不能超过 2000 字");
        }
        row.setCommentText(comment);
        annotationMapper.updateById(row);
        return toAnnotationVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAnnotation(Long annotationId) {
        Long uid = requireUserId();
        if (annotationId == null) {
            throw new BusinessException("批注ID不能为空");
        }
        GeoContentReviewAnnotation row = annotationMapper.selectById(annotationId);
        if (row == null) {
            throw new BusinessException("批注不存在");
        }
        if (!Objects.equals(row.getAuthorUserId(), uid)) {
            throw new BusinessException("只能删除自己的批注");
        }
        GeoContentReview review = reviewMapper.selectById(row.getReviewId());
        if (review == null || !"pending".equals(review.getStatus())) {
            throw new BusinessException("该轮审核已结束，不能删除批注");
        }
        requirePendingReviewer(review.getId(), uid);
        annotationMapper.deleteById(annotationId);
    }

    /** 取当前待审的审核人明细（同人多次退回再提审会追加多条，只认 pending）。 */
    private GeoContentReviewer requirePendingReviewer(Long reviewId, Long userId) {
        GeoContentReviewer mine = reviewerMapper.selectOne(new LambdaQueryWrapper<GeoContentReviewer>()
                .eq(GeoContentReviewer::getReviewId, reviewId)
                .eq(GeoContentReviewer::getUserId, userId)
                .eq(GeoContentReviewer::getResult, "pending")
                .orderByDesc(GeoContentReviewer::getId)
                .last("LIMIT 1"));
        if (mine == null) {
            Long any = reviewerMapper.selectCount(new LambdaQueryWrapper<GeoContentReviewer>()
                    .eq(GeoContentReviewer::getReviewId, reviewId)
                    .eq(GeoContentReviewer::getUserId, userId));
            if (any == null || any == 0) {
                throw new BusinessException("你不是本轮审核人");
            }
            throw new BusinessException("你已提交过审核意见");
        }
        return mine;
    }

    /** 每位审核人只取最新一条明细，用于汇总本轮结论（忽略历史退回行）。 */
    private List<GeoContentReviewer> latestReviewersOf(Long reviewId) {
        List<GeoContentReviewer> all = reviewerMapper.selectList(new LambdaQueryWrapper<GeoContentReviewer>()
                .eq(GeoContentReviewer::getReviewId, reviewId)
                .orderByAsc(GeoContentReviewer::getId));
        Map<Long, GeoContentReviewer> latest = new HashMap<>();
        for (GeoContentReviewer row : all) {
            if (row.getUserId() != null) {
                latest.put(row.getUserId(), row);
            }
        }
        return new ArrayList<>(latest.values());
    }

    /** 把投放主表当前正文写入审核轮次作为文章快照 */
    private void applyArticleSnapshot(GeoContentReview review, GeoContentPlacement placement) {
        review.setContentVersion(placement.getContentVersion() == null ? 0L : placement.getContentVersion());
        review.setContentTitle(nz(placement.getContentTitle()));
        review.setContentFormat(StringUtils.hasText(placement.getContentFormat())
                ? placement.getContentFormat()
                : Constants.CONTENT_FORMAT_LAKE);
        review.setContentBody(nz(placement.getContentBody()));
    }

    private void ensureArticleSnapshotPersisted(Long reviewId, GeoContentPlacement placement) {
        if (reviewId == null) {
            return;
        }
        GeoContentReview saved = reviewMapper.selectById(reviewId);
        if (saved == null) {
            return;
        }
        if (StringUtils.hasText(saved.getContentBody())) {
            return;
        }
        applyArticleSnapshot(saved, placement);
        reviewMapper.updateById(saved);
    }

    private void fillArticleSnapshotIfMissing(GeoContentReview review, GeoContentPlacement placement) {
        if (review == null || placement == null) {
            return;
        }
        if (StringUtils.hasText(review.getContentBody())) {
            return;
        }
        applyArticleSnapshot(review, placement);
        if (review.getId() != null) {
            reviewMapper.updateById(review);
        }
    }

    /**
     * 回填缺失的文章快照：
     * - 待审轮：直接用当前正文补（应仍是提审稿）
     * - 已结束轮：仅当 contentVersion 与当前稿一致时补（说明尚未改稿，正文仍是当时那版）
     */
    private void backfillMissingArticleSnapshots(GeoContentPlacement placement, List<GeoContentReview> reviews) {
        if (placement == null || reviews == null || reviews.isEmpty()) {
            return;
        }
        String body = nz(placement.getContentBody());
        if (!StringUtils.hasText(body)) {
            return;
        }
        Long placementVersion = placement.getContentVersion() == null ? 0L : placement.getContentVersion();
        for (GeoContentReview review : reviews) {
            if (StringUtils.hasText(review.getContentBody())) {
                continue;
            }
            boolean pending = "pending".equals(review.getStatus());
            Long reviewVersion = review.getContentVersion() == null ? 0L : review.getContentVersion();
            boolean sameVersion = Objects.equals(reviewVersion, placementVersion);
            if (!pending && !sameVersion) {
                continue;
            }
            applyArticleSnapshot(review, placement);
            reviewMapper.updateById(review);
        }
    }

    private List<GeoContentReviewRowVO> buildReviewRows(List<GeoContentReview> reviews, Long currentUserId) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        List<Long> reviewIds = reviews.stream().map(GeoContentReview::getId).toList();
        List<Long> placementIds = reviews.stream().map(GeoContentReview::getPlacementId).distinct().toList();
        Map<Long, GeoContentPlacement> placementMap = placementMapper.selectBatchIds(placementIds).stream()
                .collect(Collectors.toMap(GeoContentPlacement::getId, p -> p, (a, b) -> a));
        Map<Long, List<GeoContentReviewer>> reviewersByReview = reviewerMapper.selectList(
                        new LambdaQueryWrapper<GeoContentReviewer>()
                                .in(GeoContentReviewer::getReviewId, reviewIds)
                                .orderByAsc(GeoContentReviewer::getId))
                .stream()
                .collect(Collectors.groupingBy(GeoContentReviewer::getReviewId));
        Map<Long, GeoContentReviewAiVO> aiByReview = reviewAiService.mapByReviewIds(reviewIds);

        List<GeoContentReviewRowVO> result = new ArrayList<>();
        for (GeoContentReview review : reviews) {
            GeoContentPlacement p = placementMap.get(review.getPlacementId());
            List<GeoContentReviewer> reviewers = reviewersByReview.getOrDefault(review.getId(), List.of());
            GeoContentReviewRowVO vo = new GeoContentReviewRowVO();
            vo.setReviewId(review.getId());
            vo.setPlacementId(review.getPlacementId());
            vo.setRoundNo(review.getRoundNo());
            vo.setStageNo(reviewStageOf(review));
            vo.setStatus(review.getStatus());
            vo.setTopicName(p == null ? "" : nz(p.getTopicName()));
            vo.setTargetQuestion(p == null ? "" : nz(p.getTargetQuestion()));
            vo.setContentTitle(p == null ? "" : nz(p.getContentTitle()));
            vo.setSubmitterName(nz(review.getSubmitterName()));
            vo.setSubmitRemark(nz(review.getSubmitRemark()));
            vo.setCreateTime(review.getCreateTime());
            List<GeoContentReviewerItemVO> items = new ArrayList<>();
            StringBuilder summary = new StringBuilder();
            String myPending = null;
            String myLatest = null;
            for (GeoContentReviewer r : reviewers) {
                GeoContentReviewerItemVO item = new GeoContentReviewerItemVO();
                item.setUserId(r.getUserId());
                item.setUserName(nz(r.getUserName()));
                item.setResult(r.getResult());
                item.setComment(nz(r.getCommentText()));
                item.setActedAt(r.getActedAt());
                items.add(item);
                if (summary.length() > 0) {
                    summary.append(' ');
                }
                summary.append(nz(r.getUserName())).append(resultLabel(r.getResult()));
                if (Objects.equals(r.getUserId(), currentUserId)) {
                    myLatest = r.getResult();
                    if ("pending".equals(r.getResult())) {
                        myPending = r.getResult();
                    }
                }
            }
            if (myPending != null) {
                vo.setMyResult(myPending);
            } else if (myLatest != null) {
                vo.setMyResult(myLatest);
            }
            vo.setReviewers(items);
            vo.setReviewerSummary(summary.toString());
            vo.setAi(aiByReview.get(review.getId()));
            result.add(vo);
        }
        return result;
    }

    private void assertCanViewReviewHistory(GeoContentPlacement placement, Long uid) {
        if (Objects.equals(placement.getOwnerUserId(), uid)) {
            return;
        }
        Long asReviewer = reviewerMapper.selectCount(new LambdaQueryWrapper<GeoContentReviewer>()
                .eq(GeoContentReviewer::getPlacementId, placement.getId())
                .eq(GeoContentReviewer::getUserId, uid));
        if (asReviewer != null && asReviewer > 0) {
            return;
        }
        Long asSubmitter = reviewMapper.selectCount(new LambdaQueryWrapper<GeoContentReview>()
                .eq(GeoContentReview::getPlacementId, placement.getId())
                .eq(GeoContentReview::getSubmitterUserId, uid));
        if (asSubmitter != null && asSubmitter > 0) {
            return;
        }
        throw new BusinessException("无权查看该文章的审核历史");
    }

    private GeoContentReviewRoundVO toCurrentDraftVo(GeoContentPlacement placement) {
        GeoContentReviewRoundVO vo = new GeoContentReviewRoundVO();
        vo.setReviewId(null);
        vo.setEntryType("draft");
        vo.setRoundNo(null);
        vo.setStatus(null);
        vo.setSubmitterName(nz(placement.getOwnerName()));
        vo.setSubmitRemark("");
        vo.setContentVersion(placement.getContentVersion() == null ? 0L : placement.getContentVersion());
        vo.setContentTitle(nz(placement.getContentTitle()));
        vo.setContentFormat(StringUtils.hasText(placement.getContentFormat())
                ? placement.getContentFormat()
                : Constants.CONTENT_FORMAT_LAKE);
        vo.setContentBody(nz(placement.getContentBody()));
        vo.setHasSnapshot(StringUtils.hasText(placement.getContentTitle()) || StringUtils.hasText(placement.getContentBody()));
        vo.setCreateTime(placement.getContentUpdatedAt() != null
                ? placement.getContentUpdatedAt()
                : placement.getUpdateTime());
        vo.setReviewers(List.of());
        vo.setReviewerSummary("");
        vo.setAnnotations(List.of());
        return vo;
    }

    private GeoContentReviewRoundVO toRoundVo(
            GeoContentReview review,
            List<GeoContentReviewer> reviewers,
            List<GeoContentReviewAnnotation> annotations) {
        GeoContentReviewRoundVO vo = new GeoContentReviewRoundVO();
        vo.setReviewId(review.getId());
        vo.setEntryType("review");
        vo.setRoundNo(review.getRoundNo());
        vo.setStageNo(reviewStageOf(review));
        vo.setStatus(review.getStatus());
        vo.setSubmitterName(nz(review.getSubmitterName()));
        vo.setSubmitRemark(nz(review.getSubmitRemark()));
        vo.setContentVersion(review.getContentVersion() == null ? 0L : review.getContentVersion());
        vo.setContentTitle(nz(review.getContentTitle()));
        vo.setContentFormat(StringUtils.hasText(review.getContentFormat())
                ? review.getContentFormat()
                : Constants.CONTENT_FORMAT_LAKE);
        vo.setContentBody(nz(review.getContentBody()));
        // 文章快照以正文为准；仅有标题不算有快照
        vo.setHasSnapshot(StringUtils.hasText(review.getContentBody()));
        vo.setCreateTime(review.getCreateTime());
        List<GeoContentReviewerItemVO> items = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        for (GeoContentReviewer r : reviewers) {
            GeoContentReviewerItemVO item = new GeoContentReviewerItemVO();
            item.setUserId(r.getUserId());
            item.setUserName(nz(r.getUserName()));
            item.setResult(r.getResult());
            item.setComment(nz(r.getCommentText()));
            item.setActedAt(r.getActedAt());
            items.add(item);
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append(nz(r.getUserName())).append(resultLabel(r.getResult()));
        }
        vo.setReviewers(items);
        vo.setReviewerSummary(summary.toString());
        vo.setAnnotations(annotations.stream().map(this::toAnnotationVo).toList());
        return vo;
    }

    private Map<Long, List<GeoContentReviewAnnotation>> loadAnnotationsByReviewIds(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Map.of();
        }
        return annotationMapper.selectList(new LambdaQueryWrapper<GeoContentReviewAnnotation>()
                        .in(GeoContentReviewAnnotation::getReviewId, reviewIds)
                        .orderByAsc(GeoContentReviewAnnotation::getId))
                .stream()
                .collect(Collectors.groupingBy(GeoContentReviewAnnotation::getReviewId));
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

    private String truncateCtx(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() <= 200) {
            return text;
        }
        return text.substring(0, 200);
    }

    private String shortQuote(String raw) {
        String text = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (text.length() <= 24) {
            return text;
        }
        return text.substring(0, 24) + "…";
    }

    private Map<Long, GeoContentReview> latestPendingByPlacement(List<Long> placementIds) {
        if (placementIds.isEmpty()) {
            return Map.of();
        }
        List<GeoContentReview> pending = reviewMapper.selectList(new LambdaQueryWrapper<GeoContentReview>()
                .in(GeoContentReview::getPlacementId, placementIds)
                .eq(GeoContentReview::getStatus, "pending")
                .orderByDesc(GeoContentReview::getId));
        Map<Long, GeoContentReview> map = new HashMap<>();
        for (GeoContentReview r : pending) {
            map.putIfAbsent(r.getPlacementId(), r);
        }
        return map;
    }

    /** 每个投放取最近一轮审核（含进行中/已退回/已通过） */
    private Map<Long, GeoContentReview> latestReviewByPlacement(List<Long> placementIds) {
        if (placementIds.isEmpty()) {
            return Map.of();
        }
        List<GeoContentReview> all = reviewMapper.selectList(new LambdaQueryWrapper<GeoContentReview>()
                .in(GeoContentReview::getPlacementId, placementIds)
                .orderByDesc(GeoContentReview::getId));
        Map<Long, GeoContentReview> map = new HashMap<>();
        for (GeoContentReview r : all) {
            map.putIfAbsent(r.getPlacementId(), r);
        }
        return map;
    }

    private Map<Long, List<GeoContentReviewer>> loadReviewersByReviewIds(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Map.of();
        }
        return reviewerMapper.selectList(new LambdaQueryWrapper<GeoContentReviewer>()
                        .in(GeoContentReviewer::getReviewId, reviewIds)
                        .orderByAsc(GeoContentReviewer::getId))
                .stream()
                .collect(Collectors.groupingBy(GeoContentReviewer::getReviewId));
    }

    private List<GeoContentReview> loadReviews(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return reviewMapper.selectBatchIds(ids);
    }

    private Map<Long, SysUser> loadUsers(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));
    }

    private GeoContentPlacement requirePlacement(Long id) {
        if (id == null) {
            throw new BusinessException("内容投放ID不能为空");
        }
        GeoContentPlacement placement = placementMapper.selectById(id);
        if (placement == null) {
            throw new BusinessException("内容投放不存在");
        }
        return placement;
    }

    private Long requireUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("未登录");
        }
        return uid;
    }

    private String displayName(Long userId) {
        if (userId == null) {
            return SecurityUtils.getCurrentUsername();
        }
        SysUser user = userMapper.selectById(userId);
        return displayName(user);
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return SecurityUtils.getCurrentUsername();
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername() : String.valueOf(user.getUserId());
    }

    private static int reviewStageOf(GeoContentReview review) {
        if (review != null && review.getStageNo() != null && review.getStageNo() == Constants.CONTENT_REVIEW_STAGE2) {
            return Constants.CONTENT_REVIEW_STAGE2;
        }
        return Constants.CONTENT_REVIEW_STAGE1;
    }

    private static GeoContentReview lastOfStage(List<GeoContentReview> history, int stage) {
        GeoContentReview last = null;
        if (history == null) {
            return null;
        }
        for (GeoContentReview review : history) {
            if (reviewStageOf(review) == stage) {
                last = review;
            }
        }
        return last;
    }

    /**
     * @return 1/2 可发起；0 当前不能发起；-2 两阶段均已通过
     */
    private static int nextReviewStage(List<GeoContentReview> history, boolean skipStage1) {
        List<GeoContentReview> rows = history == null ? List.of() : history;
        if (rows.stream().anyMatch(r -> "pending".equals(r.getStatus()))) {
            return 0;
        }
        GeoContentReview last1 = lastOfStage(rows, Constants.CONTENT_REVIEW_STAGE1);
        GeoContentReview last2 = lastOfStage(rows, Constants.CONTENT_REVIEW_STAGE2);
        if (last1 == null) {
            return skipStage1 ? Constants.CONTENT_REVIEW_STAGE2 : Constants.CONTENT_REVIEW_STAGE1;
        }
        if ("rejected".equals(last1.getStatus()) || !"approved".equals(last1.getStatus())) {
            return Constants.CONTENT_REVIEW_STAGE1;
        }
        if (last2 == null || "rejected".equals(last2.getStatus())) {
            return Constants.CONTENT_REVIEW_STAGE2;
        }
        if ("approved".equals(last2.getStatus())) {
            return -2;
        }
        return 0;
    }

    private static GeoContentReviewer firstReviewerOfStage(
            List<GeoContentReview> history, int stage, Map<Long, List<GeoContentReviewer>> byReview) {
        if (history == null) {
            return null;
        }
        for (GeoContentReview review : history) {
            if (reviewStageOf(review) != stage) {
                continue;
            }
            List<GeoContentReviewer> people = byReview.getOrDefault(review.getId(), List.of());
            if (!people.isEmpty()) {
                return people.get(0);
            }
        }
        return null;
    }

    private static GeoContentReviewer lockedReviewer(
            List<GeoContentReview> history, int stage, Map<Long, List<GeoContentReviewer>> byReview) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        List<GeoContentReview> ofStage = history.stream().filter(r -> reviewStageOf(r) == stage).toList();
        if (ofStage.isEmpty()) {
            return null;
        }
        for (int i = ofStage.size() - 1; i >= 0; i--) {
            List<GeoContentReviewer> people = byReview.getOrDefault(ofStage.get(i).getId(), List.of());
            for (GeoContentReviewer person : people) {
                if ("rejected".equals(person.getResult())) {
                    return person;
                }
            }
        }
        return firstReviewerOfStage(ofStage, stage, byReview);
    }

    private static Long resolveReviewerUserId(GeoContentSubmitReviewDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getReviewerUserId() != null && dto.getReviewerUserId() > 0) {
            return dto.getReviewerUserId();
        }
        if (dto.getReviewerUserIds() != null) {
            for (Long id : dto.getReviewerUserIds()) {
                if (id != null && id > 0) {
                    return id;
                }
            }
        }
        return null;
    }

    private static String resultLabel(String result) {
        if ("approved".equals(result)) {
            return "✓";
        }
        if ("rejected".equals(result)) {
            return "✗";
        }
        return "待审";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
