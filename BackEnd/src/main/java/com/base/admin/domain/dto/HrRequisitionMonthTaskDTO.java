package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "招聘需求按月任务要求")
public class HrRequisitionMonthTaskDTO {

    @Schema(description = "主键，更新时传")
    private Long id;

    @NotNull
    @Schema(description = "招聘需求ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requisitionId;

    @NotBlank
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "核算月格式为 YYYY-MM")
    @Schema(description = "核算月 YYYY-MM", example = "2026-11", requiredMode = Schema.RequiredMode.REQUIRED)
    private String settleMonth;

    @NotBlank
    @Pattern(regexp = "^(?i)P0|P1$", message = "优先级只能是 P0 或 P1")
    @Schema(description = "P0 或 P1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String priorityCode;

    @NotNull
    @Min(0)
    @Max(999)
    @Schema(description = "入职人数门槛；P0 至少 1；P1 可与合格简历门槛并存", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer targetCount;

    @Min(0)
    @Max(999)
    @Schema(description = "合格简历份数门槛；仅 P1 使用，与入职门槛为「或」关系")
    private Integer resumeTarget;

    @NotNull
    @Min(0)
    @Max(999)
    @Schema(description = "任一达标后得几分", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer score;

    @Schema(description = "备注")
    private String remark;
}
