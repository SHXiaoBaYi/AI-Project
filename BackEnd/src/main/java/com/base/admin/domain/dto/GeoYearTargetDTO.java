package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "GEO全年目标配置")
public class GeoYearTargetDTO {

    @Schema(description = "主键（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotBlank(message = "时间段不能为空")
    @Schema(description = "时间段名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "全年")
    private String periodLabel;

    @Schema(description = "区间开始", example = "2026-01-01", nullable = true)
    private LocalDate periodStart;

    @Schema(description = "区间结束", example = "2026-12-31", nullable = true)
    private LocalDate periodEnd;

    @NotNull(message = "话题不能为空")
    @Schema(description = "话题ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long topicId;

    @NotNull(message = "目标不能为空")
    @Schema(description = "目标达成率%", requiredMode = Schema.RequiredMode.REQUIRED, example = "80.00")
    private BigDecimal targetRate;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
