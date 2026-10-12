package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO任务看板顶部汇总")
public class BoardGeoTaskSummaryVO {

    @Schema(description = "粒度", example = "week")
    private String grain;

    @Schema(description = "开始日期", example = "2026-10-06")
    private String startDate;

    @Schema(description = "结束日期", example = "2026-10-12")
    private String endDate;

    @Schema(description = "区间应到期数", example = "10")
    private long dueCount;

    @Schema(description = "区间逾期数", example = "2")
    private long overdueCount;

    @Schema(description = "区间已完成数", example = "8")
    private long doneCount;

    @Schema(description = "完成率分子", example = "8")
    private long rangeCompleted;

    @Schema(description = "完成率分母", example = "12")
    private long rangeTotal;

    @Schema(description = "完成率%", example = "66.67")
    private double completionRate;
}
