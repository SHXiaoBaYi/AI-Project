package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "待入职阶段更新")
public class HrPipelineOnboardDTO {

    @NotNull
    @Schema(description = "投递ID")
    private Long applicationId;

    @NotBlank
    @Schema(description = "子状态：SALARY/BG_COLLECT/BG_CHECK/MEDICAL/OFFER_PENDING/PENDING_ONBOARD")
    private String subStatus;

    @Schema(description = "最终 offer 金额")
    private BigDecimal salaryAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "预计入职日期")
    private LocalDate onboardDate;

    @Schema(description = "备注")
    private String remark;
}
