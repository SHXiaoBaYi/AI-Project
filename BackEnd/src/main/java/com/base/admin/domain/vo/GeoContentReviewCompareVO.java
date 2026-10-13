package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "同一文章的正文与审核历史对照")
public class GeoContentReviewCompareVO {

    @Schema(description = "投放主表ID", example = "10")
    private Long placementId;

    @Schema(description = "话题")
    private String topicName;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "当前待审轮次ID（若有）", example = "3", nullable = true)
    private Long currentReviewId;

    @Schema(description = "当前库内最新稿（撰写人最新保存，未必等于某轮提审快照）")
    private GeoContentReviewRoundVO currentDraft;

    @Schema(description = "各轮审核提审时的正文快照（按轮次升序）")
    private List<GeoContentReviewRoundVO> rounds;
}
