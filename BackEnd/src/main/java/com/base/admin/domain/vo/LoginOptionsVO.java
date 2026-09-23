package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录页公开选项")
public class LoginOptionsVO {

    @Schema(description = "是否允许账号密码登录（仅本机）", example = "true")
    private Boolean passwordLoginEnabled;

    @Schema(description = "钉钉扫码是否可用", example = "true")
    private Boolean dingTalkEnabled;

    @Schema(description = "钉钉 Client ID")
    private String clientId;

    @Schema(description = "企业 CorpId")
    private String corpId;

    @Schema(description = "是否企业专属扫码", example = "true")
    private Boolean exclusiveLogin;

    @Schema(description = "提示")
    private String message;
}
