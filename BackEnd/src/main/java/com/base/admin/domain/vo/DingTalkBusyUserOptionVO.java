package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "已绑定钉钉、可查询闲忙的用户")
public class DingTalkBusyUserOptionVO {

    @Schema(description = "系统用户ID")
    private Long userId;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "姓名")
    private String nickname;
}
