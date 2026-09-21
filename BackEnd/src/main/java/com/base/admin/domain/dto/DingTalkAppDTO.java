package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "钉钉企业内部应用配置")
public class DingTalkAppDTO {

    @NotBlank(message = "请填写 AppId")
    @Schema(description = "钉钉 AppId", requiredMode = Schema.RequiredMode.REQUIRED)
    private String appId;

    @NotBlank(message = "请填写 AgentId")
    @Schema(description = "钉钉 AgentId", requiredMode = Schema.RequiredMode.REQUIRED, example = "5015079146")
    private String agentId;

    @NotBlank(message = "请填写 Client ID")
    @Schema(description = "钉钉 Client ID / AppKey", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientId;

    @Schema(description = "Client Secret；留空表示不修改已保存的密钥")
    private String clientSecret;

    @NotNull(message = "请选择是否启用")
    @Schema(description = "是否启用 1=启用 0=停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer enabled;
}
