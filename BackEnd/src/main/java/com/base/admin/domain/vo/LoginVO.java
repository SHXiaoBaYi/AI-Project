package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "登录响应结果")
public class LoginVO {

    @Schema(description = "JWT Token")
    private String token;
}
