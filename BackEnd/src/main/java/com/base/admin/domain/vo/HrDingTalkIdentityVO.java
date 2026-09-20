package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "按系统用户手机号从钉钉解析出的身份")
public class HrDingTalkIdentityVO {

    @Schema(description = "系统用户手机号")
    private String phone;

    @Schema(description = "钉钉企业用户ID")
    private String dingtalkUserId;

    @Schema(description = "钉钉 unionId")
    private String unionId;
}
