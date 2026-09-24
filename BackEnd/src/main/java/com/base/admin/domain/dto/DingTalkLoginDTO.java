package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钉钉扫码登录请求")
public class DingTalkLoginDTO {

    @Schema(description = "钉钉 OAuth authCode（首次扫码必填；强制下线重试可改传 forceTicket）")
    private String authCode;

    @Schema(description = "是否强制登录（踢掉其他设备）", example = "false")
    private Boolean force;

    @Schema(description = "冲突后下发的一次性强制登录票据")
    private String forceTicket;
}
