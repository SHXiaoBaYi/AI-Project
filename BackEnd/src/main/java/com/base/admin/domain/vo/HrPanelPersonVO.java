package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "招聘面板指标人员明细")
public class HrPanelPersonVO {

    @Schema(description = "投递ID", example = "12")
    private Long applicationId;

    @Schema(description = "候选人", example = "张三")
    private String candidateName;

    @Schema(description = "岗位名称", example = "内容运营")
    private String jobName;

    @Schema(description = "需求部门", example = "运营中心")
    private String deptName;

    @Schema(description = "负责人", example = "王艳")
    private String ownerName;

    @Schema(description = "当前阶段", example = "背调中")
    private String stageName;

    @Schema(description = "发生时间 yyyy-MM-dd HH:mm", example = "2026-10-14 10:30")
    private String occurredAt;

    @Schema(description = "结果", example = "通过")
    private String resultLabel;
}
