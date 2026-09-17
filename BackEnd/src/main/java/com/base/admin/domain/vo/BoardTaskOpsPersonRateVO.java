package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "任务看板员工完成率行")
public class BoardTaskOpsPersonRateVO {

    @Schema(description = "员工用户ID")
    private Long userId;

    @Schema(description = "员工姓名")
    private String userName;

    @Schema(description = "分子（按时完成数 / 已完成数）")
    private long numerator;

    @Schema(description = "分母（已完成数 / 任务总数）")
    private long denominator;

    @Schema(description = "完成率%")
    private double rate;

    @Schema(description = "正常完成数（按时完成率场景）")
    private Long onTimeCount;

    @Schema(description = "提前完成数")
    private Long earlyCount;

    @Schema(description = "超时完成数")
    private Long lateCount;

    @Schema(description = "未完成数（任务完成率场景）")
    private Long openCount;
}
