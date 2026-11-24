package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "候选人批量移入黑名单")
public class HrTalentBlacklistMoveDTO {

    @NotEmpty(message = "请选择候选人")
    @Schema(description = "投递ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> applicationIds;

    @NotBlank(message = "请填写移入原因")
    @Size(max = 255, message = "移入原因不能超过255字")
    @Schema(description = "移入原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;

    @NotBlank(message = "请填写备注")
    @Size(max = 500, message = "备注不能超过500字")
    @Schema(description = "备注", requiredMode = Schema.RequiredMode.REQUIRED)
    private String remark;
}
