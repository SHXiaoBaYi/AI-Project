package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文章发布数量聚合行")
public class GeoContentPublishAggRowVO {

    @Schema(description = "日期", example = "2026-09-15")
    private String dateLabel;

    @Schema(description = "话题")
    private String topicName;

    @Schema(description = "发布人")
    private String publisherName;

    @Schema(description = "发布人用户ID")
    private Long publisherUserId;

    @Schema(description = "发布平台")
    private String publishPlatform;

    @Schema(description = "内容形态")
    private String contentForm;

    @Schema(description = "发布数量", example = "5")
    private Integer publishCount;
}
