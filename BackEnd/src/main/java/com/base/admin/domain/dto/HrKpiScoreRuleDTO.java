package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "考核计分规则")
public class HrKpiScoreRuleDTO {

    @NotNull
    @Min(1)
    @Max(100)
    @Schema(description = "考核基准分（完成率×基准分=实得分，面板展示为 得分/基准分）", example = "30")
    private Integer baseScore;

    @NotNull
    @Min(1)
    @Max(99)
    @Schema(description = "P0：完成多少人入职", example = "1")
    private Integer p0OnboardCount;

    @NotNull
    @Min(0)
    @Max(100)
    @Schema(description = "P0：记多少分", example = "2")
    private Integer p0Score;

    @NotNull
    @Min(1)
    @Max(99)
    @Schema(description = "P1：周期内合格简历份数（与入职二选一）", example = "3")
    private Integer p1QualifiedResumeCount;

    @NotNull
    @Min(1)
    @Max(99)
    @Schema(description = "P1：周期内入职人数（与合格简历二选一）", example = "1")
    private Integer p1OnboardCount;

    @NotNull
    @Min(0)
    @Max(100)
    @Schema(description = "P1：达成后记多少分", example = "1")
    private Integer p1Score;

    @NotNull
    @Min(1)
    @Max(24)
    @Schema(description = "任务默认周期：发布后几个月到期", example = "1")
    private Integer dueMonths;
}
