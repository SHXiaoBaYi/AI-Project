package com.base.admin.service;

import com.base.admin.domain.dto.GeoContentReviewAiBatchDTO;
import com.base.admin.domain.dto.GeoContentReviewAiFeedbackDTO;
import com.base.admin.domain.vo.GeoContentReviewAiVO;
import com.base.admin.domain.vo.GeoContentReviewAnnotationVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface GeoContentReviewAiService {

    /** 提审后触发；规则关闭或 AI 不可用时静默跳过 */
    void enqueueAfterSubmit(Long reviewId);

    /** 查询本轮最新 AI 预审（含规则侧 block/mode） */
    GeoContentReviewAiVO getByReviewId(Long reviewId);

    Map<Long, GeoContentReviewAiVO> mapByReviewIds(Collection<Long> reviewIds);

    /** 重新跑（仅 pending 轮次） */
    GeoContentReviewAiVO rerun(Long reviewId);

    /** 一键采纳划词 */
    List<GeoContentReviewAnnotationVO> adopt(Long reviewId);

    void feedback(GeoContentReviewAiFeedbackDTO dto);

    /** 批量触发；返回实际入队数量 */
    int batchEnqueue(GeoContentReviewAiBatchDTO dto);

    /** 人工「通过」前：若规则要求等待 AI 且未完成则抛错 */
    void assertApproveAllowed(Long reviewId, Long writerUserId);
}
