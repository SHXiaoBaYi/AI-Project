package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "修改用户状态请求参数")
public class ChangeStatusDTO {

    @NotNull(message = "User ID cannot be null")
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @NotNull(message = "Status cannot be null")
    @Schema(description = "状态（0=正常 1=停用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;
}
