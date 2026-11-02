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
    @Schema(description = "ONBOARD / EITHER")
    private String achieveMode;
    @Schema(description = "规则说明")
    private String achieveModeLabel;
    @Schema(description = "入职人数门槛")
    private Integer targetCount;
    @Schema(description = "合格简历份数门槛（P1）")
    private Integer resumeTarget;
    private Integer score;
    private String remark;
    private String updateBy;
    private String updateTime;
}
