package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "招聘面板指标明细")
public class HrPanelDetailQueryDTO extends HrPanelQueryDTO {

    @NotBlank
    @Schema(description = "指标 OPEN_JOB/BG_CHECK/PENDING_ONBOARD/TODAY_SCREEN/TODAY_PHONE/TODAY_INVITE/TODAY_INTERVIEW/TODAY_INTERVIEW_PASS",
            example = "TODAY_SCREEN")
    private String metric;
}
