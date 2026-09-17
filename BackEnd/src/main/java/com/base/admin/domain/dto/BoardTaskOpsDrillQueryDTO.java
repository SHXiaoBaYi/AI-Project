package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务看板下钻查询")
public class BoardTaskOpsDrillQueryDTO extends PageQuery {

    @Schema(description = "指标：todayDue/todayOverdue/weekDue/weekOverdue/todayDone/weekDone/onTimeRate/completionRate")
    private String metric;

    @Schema(description = "下钻层级：person=员工完成率；task=任务明细（默认）")
    private String level;

    @Schema(description = "员工用户ID（任务明细层必填；按执行人归集，无执行人则按负责人）")
    private Long personUserId;

    @Schema(description = "子筛：完成类→all|onTime|early|late；completionRate→all|done|open")
    private String subFilter;

    @Schema(description = "开始日期（③④）")
    private String startDate;

    @Schema(description = "结束日期（③④）")
    private String endDate;

    @Schema(description = "锚定今日/本周业务日")
    private String asOfDate;
}
