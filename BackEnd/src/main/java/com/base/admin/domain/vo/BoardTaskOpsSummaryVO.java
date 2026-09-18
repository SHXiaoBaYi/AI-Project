package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "任务看板汇总指标")
public class BoardTaskOpsSummaryVO {

    @Schema(description = "粒度")
    private String grain;

    @Schema(description = "周期文案前缀：今日/本周/本月/本年")
    private String periodLabel;

    @Schema(description = "当前周期到期（未完成，计划截止落在区间）")
    private long periodDue;

    @Schema(description = "当前周期超时（未完成且已逾期，计划截止落在区间）")
    private long periodOverdue;

    @Schema(description = "当前周期完成")
    private long periodDone;

    @Schema(description = "区间内按时完成数")
    private long onTimeDone;

    @Schema(description = "区间内已完成数（按时完成率分母）")
    private long rangeDone;

    @Schema(description = "按时完成率% = 按时完成/已完成")
    private double onTimeRate;

    @Schema(description = "区间内已完成数（完成率分子）")
    private long rangeCompleted;

    @Schema(description = "区间内任务总数，不含已取消（完成率分母）")
    private long rangeTotal;

    @Schema(description = "完成率% = 完成/总数")
    private double completionRate;

    @Schema(description = "筛选开始日期")
    private String startDate;

    @Schema(description = "筛选结束日期")
    private String endDate;
}
