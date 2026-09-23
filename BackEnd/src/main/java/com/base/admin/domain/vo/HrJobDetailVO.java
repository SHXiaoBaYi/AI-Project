package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "招聘看板-岗位实时明细行")
public class HrJobDetailVO {

    @Schema(description = "需求ID")
    private Long id;

    @Schema(description = "岗位名称")
    private String jobName;

    @Schema(description = "岗位状态码 OPEN/DONE/STOPPED/PAUSED")
    private String status;

    @Schema(description = "岗位状态展示")
    private String statusLabel;

    @Schema(description = "工作地")
    private String location;

    @Schema(description = "地点码 SH/XJ")
    private String locationCode;

    @Schema(description = "部门")
    private String deptName;

    @Schema(description = "负责人（多人顿号分隔）")
    private String ownerName;

    @Schema(description = "优先级 1/2/3")
    private Integer priority;

    @Schema(description = "优先级展示")
    private String priorityLabel;

    @Schema(description = "目标到岗原文")
    private String targetText;

    @Schema(description = "人数（HC）")
    private Integer headcount;

    @Schema(description = "接收日 yyyy-MM-dd")
    private String receivedDate;

    @Schema(description = "招聘天数（接收日至今，已关闭则到关闭日）")
    private Integer recruitingDays;

    @Schema(description = "日进展文案")
    private String dayProgress;

    @Schema(description = "周进展文案")
    private String weekProgress;
}
