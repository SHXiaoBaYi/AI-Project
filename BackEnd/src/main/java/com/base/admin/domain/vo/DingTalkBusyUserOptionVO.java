package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @Schema(description = "是否已绑定钉钉 1=已绑定 0=未绑定", example = "1")
    private Integer dingtalkBound;

    @JsonIgnore
    @Schema(hidden = true, description = "手机号。只在机器人里把同一号码的重复账号收成一个人，不返回给前端")
    private String phone;
}
