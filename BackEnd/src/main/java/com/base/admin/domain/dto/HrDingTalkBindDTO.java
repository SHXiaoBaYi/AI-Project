package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "绑定用户的企业钉钉身份")
public class HrDingTalkBindDTO {

    @NotNull
    @Schema(description = "系统用户")
    private Long userId;

    @Schema(description = "钉钉里登记的手机号，不填则用系统用户手机号")
    private String phone;

    @Schema(description = "企业内 userid，手机号匹配失败时手工填写")
    private String dingtalkUserId;

    @Schema(description = "unionId，和企业 userid 一起手工填写")
    private String unionId;
}
