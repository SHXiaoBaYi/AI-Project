package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "考核任务达标候选人查询")
public class HrKpiAssessmentCandidateQueryDTO {

    @NotNull(message = "岗位不能为空")
    @Schema(description = "需求岗位 ID", example = "12")
    private Long requisitionId;

    @Schema(description = "核算月任意一天；空则本月")
    private LocalDate settleMonth;

    @Schema(description = "完成方式 ONBOARD/QUALIFIED_RESUME；空则按当月达标结果自动判定", example = "ONBOARD")
    private String doneReason;
}
