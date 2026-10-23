package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.GeoContentReviewRuleDTO;
import com.base.admin.domain.dto.GeoContentReviewRuleQueryDTO;
import com.base.admin.domain.entity.GeoContentReviewRule;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface GeoContentReviewRuleService {

    PageResult<GeoContentReviewRule> list(GeoContentReviewRuleQueryDTO query);

    void create(GeoContentReviewRuleDTO dto);

    void update(GeoContentReviewRuleDTO dto);

    void delete(Long id);

    void deleteBatch(List<Long> ids);

    /** 配置了跳过一审的撰写人 */
    Set<Long> writerIdsSkippingStage1(Collection<Long> writerUserIds);

    boolean skipStage1(Long writerUserId);

    /** 无专属规则时默认开启 AI 预审；有规则则看 ai_pre_review */
    boolean aiPreReviewEnabled(Long writerUserId);

    /** assist / skip_stage1_on_pass；无规则默认 assist */
    String aiMode(Long writerUserId);

    /** AI 未完成前是否禁止人工「通过」 */
    boolean aiBlockApprove(Long writerUserId);

    GeoContentReviewRule findByWriter(Long writerUserId);
}
