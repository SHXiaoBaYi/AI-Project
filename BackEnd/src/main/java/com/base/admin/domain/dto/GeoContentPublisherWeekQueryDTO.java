package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内容投放-发布人周看板查询")
public class GeoContentPublisherWeekQueryDTO {

    @Schema(description = "周开始日期（含，建议传周一）", example = "2026-09-08")
    private String startDate;

    @Schema(description = "周结束日期（含，建议传周日）", example = "2026-09-14")
    private String endDate;

    @Schema(description = "发布人用户ID（可选，筛单人）", example = "1", nullable = true)
    private Long publisherUserId;
}
