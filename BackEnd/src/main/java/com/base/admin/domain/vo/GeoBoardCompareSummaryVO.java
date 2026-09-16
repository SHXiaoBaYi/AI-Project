package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO看板同比/环比指标摘要")
public class GeoBoardCompareSummaryVO {

    @Schema(description = "露出率%", example = "37.50")
    private Double mentionRate;

    @Schema(description = "露出率环比变化（百分点）", example = "2.50")
    private Double mentionRateMom;

    @Schema(description = "露出率同比变化（百分点）", example = "-1.20")
    private Double mentionRateYoy;

    @Schema(description = "首位露出率%", example = "70.00")
    private Double firstMentionRate;

    @Schema(description = "首位露出率环比变化（百分点）", example = "3.00")
    private Double firstMentionRateMom;

    @Schema(description = "首位露出率同比变化（百分点）", example = "1.50")
    private Double firstMentionRateYoy;

    @Schema(description = "推荐次数", example = "12")
    private Integer recommendCount;

    @Schema(description = "推荐次数环比变化", example = "2")
    private Integer recommendCountMom;

    @Schema(description = "推荐次数同比变化", example = "-1")
    private Integer recommendCountYoy;

    @Schema(description = "样本数", example = "40")
    private Integer sampleCount;

    @Schema(description = "对比说明", example = "环比=上一周期；同比=去年同期")
    private String compareHint;
}
