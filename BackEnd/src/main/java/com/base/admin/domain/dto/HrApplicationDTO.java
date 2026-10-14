package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "候选人投递保存")
public class HrApplicationDTO {

    @Schema(description = "投递ID，新增为空")
    private Long id;

    @NotBlank
    @Schema(description = "姓名")
    private String displayName;

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "性别 男/女")
    private String gender;

    @Schema(description = "年龄", example = "32", nullable = true)
    private Integer age;

    @Schema(description = "学历")
    private String education;

    @Schema(description = "人选编号")
    private String candidateNo;

    @Schema(description = "目前职位")
    private String currentTitle;

    @Schema(description = "目前公司")
    private String currentCompany;

    @NotNull
    @Schema(description = "招聘需求")
    private Long requisitionId;

    @Schema(description = "渠道")
    private String channelCode;

    @NotBlank
    @Schema(description = "当前阶段")
    private String currentStage;

    @Schema(description = "简历提交人用户，必须是系统用户")
    private Long submitterUserId;

    @Schema(description = "简历提交人姓名，由用户带出，不要手填")
    private String submitterName;

    @NotNull
    @Schema(description = "投递日期")
    private LocalDate submittedAt;
}
