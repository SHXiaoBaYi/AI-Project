package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
@Schema(description = "当前用户信息")
public class UserInfoVO {

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "昵称", example = "管理员")
    private String nickname;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "角色标识列表")
    private List<String> roles;

    @Schema(description = "权限标识集合")
    private Set<String> permissions;

    @Schema(description = "菜单树")
    private List<MenuVO> menus;
}
