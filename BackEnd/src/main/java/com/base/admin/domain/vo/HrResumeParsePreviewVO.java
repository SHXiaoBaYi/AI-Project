package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "简历解析预览（姓名/电话/邮箱，含 Boss/猎聘文件名提示）")
public class HrResumeParsePreviewVO {

    @Schema(description = "识别到的姓名，可能为空")
    private String displayName;

    @Schema(description = "识别到的电话，可能为空")
    private String phone;

    @Schema(description = "识别到的邮箱，可能为空")
    private String email;

    @Schema(description = "从 Boss/猎聘文件名识别的岗位，可能为空")
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

    @Schema(description = "汇总提示，如「未识别到姓名、电话」")
    private String tip;

    @Schema(description = "文件名")
    private String fileName;
}
