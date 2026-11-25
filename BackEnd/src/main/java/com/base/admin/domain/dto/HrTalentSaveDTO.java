package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "人才入库或更新档案")
public class HrTalentSaveDTO {

    @Schema(description = "人才ID，新增为空；合并到已有人才时传入")
    private Long candidateId;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "姓名")
    private String displayName;

    @Size(max = 32)
    @Schema(description = "电话")
    private String phone;

    @Size(max = 128)
    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "性别 男/女")
    private String gender;

    @Schema(description = "年龄", example = "32", nullable = true)
    private Integer age;

    @Size(max = 64)
    @Schema(description = "学历")
    private String education;

    @Size(max = 32)
    @Schema(description = "人选编号，空则自动生成")
    private String candidateNo;

    @Size(max = 128)
    @Schema(description = "目前职位")
    private String currentTitle;

    @Size(max = 128)
    @Schema(description = "目前公司")
    private String currentCompany;

    @Size(max = 64)
    @Schema(description = "城市")
    private String city;

    @Size(max = 32)
    @Schema(description = "工作年限，如 5年、3-5年")
    private String workYears;

    @Size(max = 64)
    @Schema(description = "期望薪资")
    private String expectedSalary;

    @Size(max = 500)
    @Schema(description = "技能标签，逗号或顿号分隔")
    private String skillTags;

    @Size(max = 128)
    @Schema(description = "学校")
    private String schoolName;

    @Size(max = 64)
    @Schema(description = "专业")
    private String major;

    @Size(max = 128)
    @Schema(description = "院校标签，如 985/211")
    private String schoolTags;

    @Schema(description = "入库时挂接的招聘需求，空则只进人才库")
    private Long requisitionId;

    @Schema(description = "渠道")
    private String channelCode;

    @Schema(description = "简历提交人用户")
    private Long submitterUserId;

    @Schema(description = "投递或入库日期，空则今天")
    private LocalDate submittedAt;

    @Schema(description = "手机号或邮箱已存在时仍新建一条人才")
    private Boolean forceNew;

    @Schema(description = "编辑档案。入库即使合并到已有人才也要传 false")
    private Boolean editing;

    @Schema(description = "编辑时上传了新简历，且当前没有投递，则补一条未挂岗投递承接简历")
    private Boolean ensureApplication;
}
