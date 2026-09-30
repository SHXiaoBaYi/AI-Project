package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钉钉 H5 免登 / OAuth 登录请求")
public class DingTalkSsoDTO {

    @Schema(description = "钉钉授权码（企业免登 requestAuthCode 或 OAuth code）")
    private String authCode;

    @Schema(description = "登录模式：corp=企业内免登；oauth=开放平台扫码/授权", example = "corp")
    private String mode;

    @Schema(description = "是否强制登录（踢掉其他设备）；H5 免登建议 true", example = "true")
    private Boolean force;

    @Schema(description = "冲突后下发的一次性强制登录票据")
    private String forceTicket;
}
