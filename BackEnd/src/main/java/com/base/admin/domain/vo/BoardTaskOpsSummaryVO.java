package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "任务看板汇总指标")
public class BoardTaskOpsSummaryVO {

    @Schema(description = "锚定业务日 yyyy-MM-dd")
    private String asOfDate;

    @Schema(description = "今日到期（未完成，计划截止落在今日）")
    private long todayDue;

    @Schema(description = "今日超时（未完成且已逾期，计划截止落在今日）")
    private long todayOverdue;

    @Schema(description = "本周到期（未完成，计划截止落在本周）")
    private long weekDue;

    @Schema(description = "本周超时（未完成且已逾期，计划截止落在本周）")
    private long weekOverdue;

    @Schema(description = "今日完成")
    private long todayDone;

    @Schema(description = "本周完成")
    private long weekDone;

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

    @Schema(description = "③④使用的开始日期")
    private String startDate;

    @Schema(description = "③④使用的结束日期")
    private String endDate;
}
