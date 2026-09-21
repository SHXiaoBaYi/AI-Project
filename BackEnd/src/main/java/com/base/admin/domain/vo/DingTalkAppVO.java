package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钉钉企业内部应用配置")
public class DingTalkAppVO {

    @Schema(description = "钉钉 AppId")
    private String appId;

    @Schema(description = "钉钉 AgentId")
    private String agentId;

    @Schema(description = "钉钉 Client ID / AppKey")
    private String clientId;

    @Schema(description = "Client Secret 脱敏展示")
    private String clientSecretMasked;

    @Schema(description = "是否已保存 Client Secret")
    private Boolean hasClientSecret;

    @Schema(description = "是否启用 1=启用 0=停用", example = "1")
    private Integer enabled;
}
