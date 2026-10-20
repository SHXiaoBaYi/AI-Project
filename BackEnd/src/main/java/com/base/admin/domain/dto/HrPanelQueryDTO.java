package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "招聘面板筛选")
public class HrPanelQueryDTO {

    @Schema(description = "岗位状态 OPEN招聘中 CLOSED已关闭", example = "OPEN")
    private String panelStatus;

    @Schema(description = "需求部门ID，含下级", example = "1", nullable = true)
    private Long deptId;

    @Schema(description = "岗位名称，模糊匹配", example = "运营")
    private String jobName;

    @Schema(description = "负责人用户ID（单选，兼容）", example = "1", nullable = true)
    private Long ownerUserId;

    @Schema(description = "负责人用户ID（多选，任一命中）")
    private List<Long> ownerUserIds;

    @Schema(description = "岗位优先级编码 P0/P1，独立于紧急/优先/常规", example = "P0")
    private String jobPriorityCode;

    @Schema(description = "工作地址字典ID", example = "1", nullable = true)
    private Long workAddressId;

    @Schema(description = "统计开始日（含）yyyy-MM-dd，作用于背调/待入职/筛选/电话/约面/面试等指标", example = "2026-10-01")
    private String startDate;

    @Schema(description = "统计结束日（含）yyyy-MM-dd", example = "2026-10-20")
    private String endDate;

    @Schema(description = "日期粒度 day/week/month/year", example = "day")
    private String grain;

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
