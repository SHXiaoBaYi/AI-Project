package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "绑定用户的企业钉钉身份")
public class HrDingTalkBindDTO {

    @NotNull
    @Schema(description = "系统用户。钉钉身份按该用户手机号从钉钉查询，不能手工指定", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
}
