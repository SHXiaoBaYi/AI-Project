package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "GEO日监测批量分组（同一天+同一话题+关键字，多平台）")
public class GeoDailyBulkGroupDTO {

    @NotNull(message = "巡查日期不能为空")
    @Schema(description = "巡查日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-15")
    private LocalDate inspectDate;

    @NotNull(message = "话题不能为空")
    @Schema(description = "话题ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long topicId;

    @NotBlank(message = "关键字不能为空")
    @Schema(description = "关键字/提问问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "新疆适合寄内地的礼品")
    private String keyword;

    @Valid
    @NotEmpty(message = "请至少填写一个平台")
    @Schema(description = "各平台监测指标")
    private List<GeoDailyPlatformItemDTO> items;
}
