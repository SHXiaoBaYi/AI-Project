package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "招聘面板统计")
public class HrPanelStatsVO {

    @Schema(description = "目前在招岗位数", example = "12")
    private Integer openJobCount;

    @Schema(description = "背调中人数", example = "3")
    private Integer bgCheckCount;

    @Schema(description = "待入职人数", example = "2")
    private Integer pendingOnboardCount;

    @Schema(description = "今日筛选简历人数", example = "5")
    private Integer todayScreenCount;

    @Schema(description = "今日电话沟通人数", example = "4")
    private Integer todayPhoneCount;

    @Schema(description = "今日约面人数", example = "3")
    private Integer todayInviteCount;

    @Schema(description = "今日实际面试人数", example = "2")
    private Integer todayInterviewCount;

    @Schema(description = "今日通过面试人数", example = "1")
    private Integer todayInterviewPassCount;
}
