package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("sys_role_menu")
@Schema(description = "角色菜单关联")
public class SysRoleMenu implements Serializable {

    @Schema(description = "角色ID", example = "1")
    private Long roleId;

    @Schema(description = "菜单ID", example = "1")
    private Long menuId;

    @TableLogic(value = "1", delval = "0")
    @Schema(description = "是否有效（1=有效 0=已删除）", example = "1")
    private Integer isActive;
}
