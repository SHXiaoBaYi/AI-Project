package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "简历初筛")
public class HrPipelineScreenDTO {

    @NotNull
    @Schema(description = "投递ID", example = "21")
    private Long applicationId;

    @NotBlank
    @Schema(description = "初筛结果 PASS合适 / FAIL不合适")
    private String result;

    @Schema(description = "不合适原因（结果为 FAIL 时必填）")
    private String rejectReason;

    @Schema(description = "备注")
    private String remark;
}
