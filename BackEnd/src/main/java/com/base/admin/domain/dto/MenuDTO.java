package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "菜单新增/修改请求参数")
public class MenuDTO {

    @Schema(description = "菜单ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long menuId;

    @NotBlank(message = "Menu name cannot be empty")
    @Schema(description = "菜单名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "系统管理")
    private String menuName;

    @Schema(description = "父菜单ID（0表示顶级菜单）", example = "0")
    private Long parentId;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "路由路径", example = "/system")
    private String path;

    @Schema(description = "组件路径", example = "system/user/index")
    private String component;

    @NotBlank(message = "Menu type cannot be empty")
    @Schema(description = "菜单类型（M=目录 C=菜单 F=按钮）", requiredMode = Schema.RequiredMode.REQUIRED, example = "M")
    private String menuType;

    @Schema(description = "权限标识", example = "system:user:list")
    private String perms;

    @Schema(description = "图标", example = "setting")
    private String icon;

    @Schema(description = "是否可见（0=显示 1=隐藏）", example = "0")
    private Integer visible;

    @Schema(description = "状态（0=正常 1=停用）", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
