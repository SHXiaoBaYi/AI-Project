package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "内容投放-发布人周看板明细查询")
public class GeoContentPublisherWeekDetailQueryDTO {

    @NotBlank(message = "周开始日期不能为空")
    @Schema(description = "周开始日期（含）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-08")
    private String startDate;

    @NotBlank(message = "周结束日期不能为空")
    @Schema(description = "周结束日期（含）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-14")
    private String endDate;

    @NotNull(message = "发布人用户ID不能为空")
    @Schema(description = "发布人用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long publisherUserId;

    @NotBlank(message = "指标类型不能为空")
    @Schema(
            description = "指标：produced产出 / pendingReview待审 / published已发布 / pendingProduce待产出 / videoPublished视频已发布 / videoPendingReview视频待审",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "published")
    private String metric;
}
