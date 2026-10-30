package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "考核计分规则")
public class HrKpiScoreRuleVO {

    @Schema(description = "考核基准分（完成率×基准分=实得分）", example = "30")
    private Integer baseScore;

    @Schema(description = "P0：完成多少人入职")
    private Integer p0OnboardCount;

    @Schema(description = "P0：记多少分")
    private Integer p0Score;

    @Schema(description = "P1：周期内合格简历份数（与入职二选一）")
    private Integer p1QualifiedResumeCount;

    @Schema(description = "P1：周期内入职人数（与合格简历二选一）")
    private Integer p1OnboardCount;

    @Schema(description = "P1：达成后记多少分")
    private Integer p1Score;

    @Schema(description = "任务默认周期：发布后几个月到期")
    private Integer dueMonths;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近更新时间")
    private LocalDateTime updateTime;
}
