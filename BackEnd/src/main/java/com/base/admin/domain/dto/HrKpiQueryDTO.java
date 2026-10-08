package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "招聘 KPI 看板筛选")
public class HrKpiQueryDTO {

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "粒度 day/week/month/quarter")
    private String grain;

    @Schema(description = "招聘负责人")
    private Long ownerUserId;

    @Schema(description = "重要性 1高 2中 3低")
    private Integer importanceLevel;

    @Schema(description = "紧急程度 1紧急 2常规")
    private Integer urgencyLevel;

    @Schema(description = "难度 1高 2中 3低")
    private Integer difficultyLevel;
}
