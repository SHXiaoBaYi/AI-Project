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

    @Schema(description = "区间内筛选简历=新增候选人数", example = "8")
    private Integer todayScreenCount;

    @Schema(description = "区间内电话沟通人数", example = "4")
    private Integer todayPhoneCount;

    @Schema(description = "区间内约面人数", example = "3")
    private Integer todayInviteCount;

    @Schema(description = "区间内实际面试人数", example = "2")
    private Integer todayInterviewCount;

    @Schema(description = "区间内通过面试人数", example = "1")
    private Integer todayInterviewPassCount;
}
