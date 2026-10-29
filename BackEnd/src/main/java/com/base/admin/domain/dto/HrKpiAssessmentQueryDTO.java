package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "考核得分面板查询")
public class HrKpiAssessmentQueryDTO {

    @Schema(description = "核算月任意一天，取该自然月内到期的任务；空则本月")
    private LocalDate settleMonth;

    @Schema(description = "负责人多选")
    private List<Long> ownerUserIds;

    @Schema(description = "任务状态 OPEN/DONE/CANCELLED，空=全部有效")
    private String status;
}
