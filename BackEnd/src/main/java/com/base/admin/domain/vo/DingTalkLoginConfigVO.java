package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钉钉扫码登录前端配置（公开）")
public class DingTalkLoginConfigVO {

    @Schema(description = "是否已启用钉钉扫码登录", example = "1")
    private Integer enabled;

    @Schema(description = "钉钉 Client ID / AppKey")
    private String clientId;

    @Schema(description = "企业 CorpId；有值时前端可限制为企业专属扫码")
    private String corpId;

    @Schema(description = "是否强制企业专属账号扫码", example = "true")
    private Boolean exclusiveLogin;

    @Schema(description = "提示文案")
    private String message;
}
