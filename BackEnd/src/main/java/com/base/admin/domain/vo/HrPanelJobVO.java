package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "招聘面板岗位行")
public class HrPanelJobVO {

    @Schema(description = "需求ID", example = "1")
    private Long id;

    @Schema(description = "岗位状态码 OPEN/CLOSED", example = "OPEN")
    private String panelStatus;

    @Schema(description = "岗位状态 招聘中/已关闭", example = "招聘中")
    private String panelStatusLabel;

    @Schema(description = "需求部门", example = "运营中心")
    private String deptName;

    @Schema(description = "岗位名称", example = "内容运营")
    private String jobName;

    @Schema(description = "HC 需求人数", example = "2")
    private Integer headcount;

    @Schema(description = "负责人", example = "王艳")
    private String ownerName;

    @Schema(description = "岗位优先级编码", example = "P0")
    private String jobPriorityCode;

    @Schema(description = "岗位优先级展示", example = "P0 本月必须到岗")
    private String jobPriorityLabel;

    @Schema(description = "工作地点", example = "上海")
    private String workLocation;

    @Schema(description = "接收日 yyyy-MM-dd", example = "2026-10-01")
    private String receivedDate;

    @Schema(description = "任务截止日期 yyyy-MM-dd", example = "2026-10-31")
    private String deadlineDate;

    @Schema(description = "招聘天数", example = "14")
    private Integer recruitingDays;

    @Schema(description = "累计进展", example = "初筛合适3人，约面2人")
    private String progressSummary;
}
