package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "菜单树节点")
public class MenuVO {

    @Schema(description = "菜单ID", example = "1")
    private Long menuId;

    @Schema(description = "菜单名称", example = "系统管理")
    private String menuName;

    @Schema(description = "路由路径", example = "/system")
    private String path;

    @Schema(description = "组件路径", example = "system/user/index")
    private String component;

    @Schema(description = "图标", example = "setting")
    private String icon;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "状态（0=正常 1=停用）", example = "0")
    private Integer status;

    @Schema(description = "子菜单")
    private List<MenuVO> children;
}
