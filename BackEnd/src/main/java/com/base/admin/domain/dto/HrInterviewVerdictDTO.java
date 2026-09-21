package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "面试联合评价")
public class HrInterviewVerdictDTO {

    @NotNull
    @Schema(description = "候选人投递")
    private Long applicationId;

    @NotNull
    @Schema(description = "轮次")
    private Integer roundNo;

    @NotBlank
    @Schema(description = "结论 PASS通过 FAIL未通过 PENDING待定")
    private String conclusion;

    @Schema(description = "联合评价说明")
    private String comment;
}
