package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "简历解析预览（AI 优先识别，供新增时预填核对）")
public class HrResumeParsePreviewVO {

    @Schema(description = "识别到的姓名，可能为空")
    private String displayName;

    @Schema(description = "性别：男/女，可能为空")
    private String gender;

    @Schema(description = "年龄，可能为空")
    private Integer age;

    @Schema(description = "学历，可能为空")
    private String education;

    @Schema(description = "目前职位，可能为空")
    private String currentTitle;

    @Schema(description = "目前公司，可能为空")
    private String currentCompany;

    @Schema(description = "识别到的电话，可能为空")
    private String phone;

    @Schema(description = "识别到的邮箱，可能为空")
    private String email;

    @Schema(description = "渠道码（匹配系统字典），可能为空", example = "BOSS")
    private String channelCode;

    @Schema(description = "渠道名（展示用），可能为空")
    private String channelName;

    @Schema(description = "从文件名识别的岗位提示，可能为空")
    private String jobHint;

    @Schema(description = "从文件名识别的城市，可能为空")
    private String cityHint;

    @Schema(description = "从文件名识别的薪资区间，可能为空")
    private String salaryHint;

    @Schema(description = "从文件名识别的工作年限，可能为空")
    private String yearsHint;

    @Schema(description = "是否识别到姓名")
    private Boolean nameFound;

    @Schema(description = "是否识别到电话")
    private Boolean phoneFound;

    @Schema(description = "是否识别到邮箱")
    private Boolean emailFound;

    @Schema(description = "实际采用的识别方式：AI / RULE / MIXED")
    private String parseMode;

    @Schema(description = "汇总提示")
    private String tip;

    @Schema(description = "文件名")
    private String fileName;
}
