package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "岗位看板候选人行")
public class HrJobCandidateVO {

    @Schema(description = "投递ID", example = "21")
    private Long applicationId;

    @Schema(description = "候选人ID", example = "8")
    private Long candidateId;

    @Schema(description = "姓名")
    private String displayName;

    @Schema(description = "是否未处理（已录入）")
    private Boolean pending;

    @Schema(description = "性别 男/女")
    private String gender;

    @Schema(description = "年龄", example = "41", nullable = true)
    private Integer age;

    @Schema(description = "学历")
    private String education;

    @Schema(description = "基本信息，如 男 | 41 | 本科")
    private String basicInfo;

    @Schema(description = "人选编号")
    private String candidateNo;

    @Schema(description = "目前职位")
    private String currentTitle;

    @Schema(description = "目前公司")
    private String currentCompany;

    @Schema(description = "提交时间 yyyy-MM-dd")
    private String recommendedAt;

    @Schema(description = "剩余处理小时，超时为负", example = "47.49", nullable = true)
    private Double remainingHours;

    @Schema(description = "提交人")
    private String referrerName;

    @Schema(description = "当前阶段码")
    private String currentStage;

    @Schema(description = "当前阶段名")
    private String stageName;

    @Schema(description = "简历文件名")
    private String resumeName;
}
