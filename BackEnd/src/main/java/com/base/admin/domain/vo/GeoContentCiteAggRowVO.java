package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内容AI收录率聚合行")
public class GeoContentCiteAggRowVO {

    @Schema(description = "日期", example = "2026-09-15")
    private String dateLabel;

    @Schema(description = "话题")
    private String topicName;

    @Schema(description = "发布人")
    private String publisherName;

    @Schema(description = "发布平台")
    private String publishPlatform;

    @Schema(description = "AI平台")
    private String aiPlatform;

    @Schema(description = "成功发布数", example = "10")
    private Integer successCount;

    @Schema(description = "被收录数（有引用的成功发布）", example = "4")
    private Integer citedCount;

    @Schema(description = "收录率%", example = "40.00")
    private Double citeRate;
}
