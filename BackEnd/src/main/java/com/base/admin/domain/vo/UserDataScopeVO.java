package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "用户数据权限配置")
public class UserDataScopeVO {

    @Schema(description = "被配置用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "DEFAULT / PERSON / SELF", example = "PERSON")
    private String mode;

    @Schema(description = "可见人员用户ID")
    private List<Long> targetUserIds = new ArrayList<>();
}
