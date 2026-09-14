package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
@Schema(description = "系统菜单实体")
public class SysMenu extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "菜单ID", example = "1")
    private Long menuId;

    @Schema(description = "菜单名称", example = "系统管理")
    private String menuName;

    @Schema(description = "父菜单ID（0表示顶级菜单）", example = "0")
    private Long parentId;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "路由路径", example = "/system")
    private String path;

    @Schema(description = "组件路径", example = "system/user/index")
    private String component;

    @Schema(description = "菜单类型（M=目录 C=菜单 F=按钮）", example = "M")
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
