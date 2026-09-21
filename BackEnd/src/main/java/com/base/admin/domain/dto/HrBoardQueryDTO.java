package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "招聘看板筛选")
public class HrBoardQueryDTO {

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "日期粒度 day/week/month/year")
    private String grain;

    @Schema(description = "部门，含下级")
    private Long deptId;

    @Schema(description = "工作地 SH/XJ")
    private String locationCode;

    @Schema(description = "渠道")
    private String channelCode;

    @Schema(description = "需求")
    private Long requisitionId;

    @Schema(description = "招聘负责人")
    private Long ownerUserId;

    @Schema(description = "优先级 1/2/3")
    private Integer priority;

    @Schema(description = "岗位状态")
    private String status;

    @Schema(description = "岗位名称，模糊匹配")
    private String jobName;

    @Schema(description = "岗位类别，按岗位名称精确匹配")
    private String jobCategory;

    @Schema(description = "招聘周期下钻的岗位；空表示各岗位平均周期")
    private String cycleJob;

    @Schema(description = "目标到岗说明，模糊匹配")
    private String targetText;

    @Schema(description = "候选人姓名，模糊匹配")
    private String candidateName;

    @Schema(description = "阶段码")
    private String stageCode;

    @Schema(description = "简历提交人，模糊匹配")
    private String submitterName;

    @Schema(description = "简历提交人用户")
    private Long submitterUserId;

    @Schema(description = "下钻类型 STAGE/HC/INTERVIEW/INTERVIEWER/FAIL_REASON/VOLUME")
    private String drillKind;

    @Schema(description = "HC指标 DEMAND/ARRIVED/GAP/CLOSED/FROZEN/RATE")
    private String hcMetric;

    @Schema(description = "面试指标 PENDING_INVITE/INVITED/SHOW_UP/ROUND1/RETEST/FINAL/ROUND1_PASS/RETEST_PASS/FINAL_PASS/NO_SHOW")
    private String interviewMetric;

    @Schema(description = "面试淘汰原因")
    private String failReason;

    @Schema(description = "面试官通过率/面试量下钻的日期桶")
    private String axis;

    @Schema(description = "面试轮次")
    private Integer roundNo;

    @Schema(description = "面试官用户")
    private Long interviewerUserId;

    @Schema(description = "true 只看当前用户尚未填写结论的成功邀约")
    private Boolean mine;
}
