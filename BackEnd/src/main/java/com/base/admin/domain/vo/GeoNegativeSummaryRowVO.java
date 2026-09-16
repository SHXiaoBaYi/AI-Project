package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO负面/错误内容汇总行")
public class GeoNegativeSummaryRowVO {

    @Schema(description = "巡查日期", example = "2026-09-15")
    private String inspectDate;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "话题名称")
    private String topicName;

    @Schema(description = "AI平台")
    private String platform;

    @Schema(description = "话题类型（日巡查/周巡查）", example = "日巡查")
    private String termType;

    @Schema(description = "负面条数", example = "2")
    private Integer negativeCount;
}
