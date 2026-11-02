package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "招聘需求按月任务要求")
public class HrRequisitionMonthTaskVO {

    private Long id;
    private Long requisitionId;
    private String jobName;
    @Schema(description = "核算月 YYYY-MM")
    private String settleMonth;
    @Schema(description = "P0 / P1")
    private String priorityCode;
    @Schema(description = "ONBOARD / QUALIFIED_RESUME")
    private String achieveMode;
    @Schema(description = "达成方式中文")
    private String achieveModeLabel;
    private Integer targetCount;
    private Integer score;
    private String remark;
    private String updateBy;
    private String updateTime;
}
