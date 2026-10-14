package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "GEO文章审核规则新增/修改")
public class GeoContentReviewRuleDTO {

    @Schema(description = "规则ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotNull(message = "请选择撰写人")
    @Schema(description = "撰写人用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Long writerUserId;

    @NotNull(message = "请选择是否跳过一审")
    @Schema(description = "是否跳过一审 1=是 0=否", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer skipStage1;

    @Schema(description = "备注")
    private String remark;
}
