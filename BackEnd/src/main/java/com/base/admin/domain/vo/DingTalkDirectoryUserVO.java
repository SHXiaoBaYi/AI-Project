package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "应用从钉钉通讯录读到的已加入成员")
public class DingTalkDirectoryUserVO {

    @Schema(description = "钉钉姓名")
    private String name;

    @Schema(description = "钉钉返回的手机号，空表示接口没给")
    private String mobile;

    @Schema(description = "国家码")
    private String stateCode;

    @Schema(description = "分机号")
    private String telephone;

    @Schema(description = "是否企业账号")
    private Boolean exclusiveAccount;

    @Schema(description = "是否对接口隐藏手机号")
    private Boolean hideMobile;

    @Schema(description = "是否已激活")
    private Boolean active;

    @Schema(description = "钉钉 userid")
    private String userid;

    @Schema(description = "钉钉 unionId")
    private String unionId;
}
