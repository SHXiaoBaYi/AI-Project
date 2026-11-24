package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "黑名单人才移回正常人才库")
public class HrTalentBlacklistRestoreDTO {

    @NotEmpty(message = "请选择黑名单人才")
    @Schema(description = "黑名单记录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @NotBlank(message = "请填写移出原因")
    @Size(max = 255, message = "移出原因不能超过255字")
    @Schema(description = "移出原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;

    @NotBlank(message = "请填写备注")
    @Size(max = 500, message = "备注不能超过500字")
    @Schema(description = "备注", requiredMode = Schema.RequiredMode.REQUIRED)
    private String remark;
}
