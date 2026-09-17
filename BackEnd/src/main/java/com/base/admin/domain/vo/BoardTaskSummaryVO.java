package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "统一看板-任务汇总")
public class BoardTaskSummaryVO {

    @Schema(description = "任务总数")
    private long total;

    @Schema(description = "待处理（含待分配）")
    private long pending;

    @Schema(description = "进行中")
    private long doing;

    @Schema(description = "已完成")
    private long done;

    @Schema(description = "已取消")
    private long cancelled;

    @Schema(description = "逾期数")
    private long overdue;

    @Schema(description = "完成率%")
    private double completionRate;

    @Schema(description = "状态分布")
    private List<BoardChartPointVO> statusChart = new ArrayList<>();

    @Schema(description = "优先级分布")
    private List<BoardChartPointVO> priorityChart = new ArrayList<>();

    @Schema(description = "负责人负荷（未完成）")
    private List<BoardChartPointVO> ownerLoadChart = new ArrayList<>();

    @Schema(description = "每日新建/完成趋势")
    private List<BoardChartPointVO> dailyTrendChart = new ArrayList<>();
}
