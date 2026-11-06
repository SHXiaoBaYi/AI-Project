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

    @Schema(description = "是否未处理")
    private Boolean pending;

    @Schema(description = "基本信息，如 男 | 28 | 本科")
    private String basicInfo;

    @Schema(description = "人选编号")
    private String candidateNo;

    @Schema(description = "目前职位")
    private String currentTitle;

    @Schema(description = "目前公司")
    private String currentCompany;

    @Schema(description = "提交时间 yyyy-MM-dd")
    private String recommendedAt;

    @Schema(description = "未处理时剩余小时；已处理为 null", nullable = true)
    private Double remainingHours;

    @Schema(description = "提交人")
    private String referrerName;

    @Schema(description = "简历文件名")
    private String resumeName;

    @Schema(description = "最近一次 AI 分析分数", nullable = true)
    private Integer aiScore;

    @Schema(description = "有效 AI 分析份数")
    private Integer aiCount;

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
