package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "钉钉扫码登录请求")
public class DingTalkLoginDTO {

    @NotBlank(message = "缺少钉钉授权码")
    @Schema(description = "钉钉 OAuth authCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String authCode;

    @Schema(description = "是否强制登录（踢掉其他设备）", example = "false")
    private Boolean force;
}
