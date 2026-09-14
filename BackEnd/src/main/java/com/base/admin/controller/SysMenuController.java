package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.MenuDTO;
import com.base.admin.domain.entity.SysMenu;
import com.base.admin.domain.vo.MenuVO;
import com.base.admin.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "菜单管理", description = "系统菜单增删改查及树形结构接口")
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @Operation(summary = "查询菜单列表")
    @GetMapping("/list")
    @RequiresPermission("system:menu:list")
    public Result<List<SysMenu>> list() {
        return Result.ok(menuService.list());
    }

    @Operation(summary = "查询菜单树形结构")
    @GetMapping("/tree")
    @RequiresPermission("system:menu:list")
    public Result<List<MenuVO>> tree() {
        return Result.ok(menuService.tree());
    }

    @Operation(summary = "根据ID查询菜单")
    @GetMapping("/{menuId}")
    @RequiresPermission("system:menu:list")
    public Result<SysMenu> getById(@PathVariable Long menuId) {
        return Result.ok(menuService.getById(menuId));
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    @RequiresPermission("system:menu:add")
    @Log(title = "菜单管理", businessType = 1)
    public Result<Void> create(@Valid @RequestBody MenuDTO dto) {
        menuService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改菜单")
    @PutMapping
    @RequiresPermission("system:menu:edit")
    @Log(title = "菜单管理", businessType = 2)
    public Result<Void> update(@Valid @RequestBody MenuDTO dto) {
        menuService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/{menuId}")
    @RequiresPermission("system:menu:delete")
    @Log(title = "菜单管理", businessType = 3)
    public Result<Void> delete(@PathVariable Long menuId) {
        menuService.delete(menuId);
        return Result.ok();
    }
}
