package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.RoleDTO;
import com.base.admin.domain.dto.RoleMenuDTO;
import com.base.admin.domain.vo.RoleVO;
import com.base.admin.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "角色管理", description = "系统角色增删改查及菜单分配接口")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "分页查询角色列表")
    @GetMapping("/list")
    @RequiresPermission("system:role:list")
    public Result<PageResult<RoleVO>> list(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(roleService.list(roleName, roleKey, status, pageNum, pageSize));
    }

    @Operation(summary = "根据ID查询角色")
    @GetMapping("/{roleId}")
    @RequiresPermission("system:role:list")
    public Result<RoleVO> getById(@PathVariable Long roleId) {
        return Result.ok(roleService.getById(roleId));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @RequiresPermission("system:role:add")
    @Log(title = "角色管理", businessType = 1)
    public Result<Void> create(@Valid @RequestBody RoleDTO dto) {
        roleService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改角色")
    @PutMapping
    @RequiresPermission("system:role:edit")
    @Log(title = "角色管理", businessType = 2)
    public Result<Void> update(@Valid @RequestBody RoleDTO dto) {
        roleService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    @RequiresPermission("system:role:delete")
    @Log(title = "角色管理", businessType = 3)
    public Result<Void> delete(@PathVariable Long roleId) {
        roleService.delete(roleId);
        return Result.ok();
    }

    @Operation(summary = "分配角色菜单")
    @PutMapping("/assignMenus")
    @RequiresPermission("system:role:edit")
    @Log(title = "角色管理-分配菜单", businessType = 2)
    public Result<Void> assignMenus(@Valid @RequestBody RoleMenuDTO dto) {
        roleService.assignMenus(dto);
        return Result.ok();
    }
}
