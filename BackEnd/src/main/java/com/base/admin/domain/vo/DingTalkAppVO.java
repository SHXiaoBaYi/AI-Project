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

    @Schema(description = "企业 CorpId；填写后扫码登录限制为企业专属账号")
    private String corpId;

    @Schema(description = "Client Secret 脱敏展示")
    private String clientSecretMasked;

    @Schema(description = "是否已保存 Client Secret")
    private Boolean hasClientSecret;

    @Schema(description = "是否启用 1=启用 0=停用", example = "1")
    private Integer enabled;

    @Schema(description = "联调账号机器人路由：local=本机，online=线上（wangfangyang/thh/tbb 共用；其他人固定 online）")
    private String robotRoute;

    @Schema(description = "当前登录人是否可改 robotRoute（wangfangyang / thh / tbb）")
    private Boolean canEditRobotRoute;
}
