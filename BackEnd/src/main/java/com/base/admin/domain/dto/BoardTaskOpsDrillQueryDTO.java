package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务看板下钻查询")
public class BoardTaskOpsDrillQueryDTO extends PageQuery {

    @Schema(description = "指标：periodDue/periodOverdue/periodDone/onTimeRate/completionRate")
    private String metric;

    @Schema(description = "下钻层级：person=员工完成率；task=任务明细（默认）")
    private String level;

    @Schema(description = "完成率下钻到员工后的用户ID")
    private Long personUserId;

    @Schema(description = "子筛：完成类→all|onTime|early|late；completionRate→all|done|open")
    private String subFilter;

    @Schema(description = "开始日期")
    private String startDate;

    @Schema(description = "结束日期")
    private String endDate;

    @Schema(description = "粒度")
    private String grain;

    @Schema(description = "人员筛选：执行人或负责人用户ID")
    private Long filterUserId;

    @Schema(description = "任务类型")
    private String taskType;
}
