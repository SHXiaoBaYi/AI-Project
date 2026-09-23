package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.ChangeStatusDTO;
import com.base.admin.domain.dto.ResetPwdDTO;
import com.base.admin.domain.dto.UserDTO;
import com.base.admin.domain.dto.UserPageQueryDTO;
import com.base.admin.domain.dto.UserRoleDTO;
import com.base.admin.domain.vo.UserImportResultVO;
import com.base.admin.domain.vo.UserVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "用户管理", description = "系统用户查询、编辑与角色分配（新增/删除/导入导出/重置密码已关闭，新用户走钉钉扫码注册）")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @Operation(summary = "分页查询用户列表")
    @PostMapping("/list")
    @RequiresPermission("system:user:list")
    public Result<PageResult<UserVO>> list(@Valid @RequestBody UserPageQueryDTO query) {
        return Result.ok(userService.list(query));
    }

    @Operation(summary = "根据ID查询用户")
    @GetMapping("/{userId}")
    @RequiresPermission("system:user:list")
    public Result<UserVO> getById(@PathVariable Long userId) {
        return Result.ok(userService.getById(userId));
    }

    @Operation(summary = "新增用户（已关闭）")
    @PostMapping
    @RequiresPermission("system:user:add")
    @Log(title = "用户管理", businessType = 1)
    public Result<Void> create(@Valid @RequestBody UserDTO dto) {
        userService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改用户")
    @PutMapping
    @RequiresPermission("system:user:edit")
    @Log(title = "用户管理", businessType = 2)
    public Result<Void> update(@Valid @RequestBody UserDTO dto) {
        userService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除用户（已关闭）")
    @DeleteMapping("/{userId}")
    @RequiresPermission("system:user:delete")
    @Log(title = "用户管理", businessType = 3)
    public Result<Void> delete(@PathVariable Long userId) {
        userService.delete(userId);
        return Result.ok();
    }

    @Operation(summary = "批量删除用户（已关闭）")
    @DeleteMapping("/batch")
    @RequiresPermission("system:user:delete")
    @Log(title = "用户管理-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        userService.deleteBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "重置用户密码（已关闭）")
    @PutMapping("/resetPwd")
    @RequiresPermission("system:user:resetPwd")
    @Log(title = "用户管理-重置密码", businessType = 2)
    public Result<Void> resetPwd(@Valid @RequestBody ResetPwdDTO dto) {
        userService.resetPwd(dto.getUserId(), dto.getPassword());
        return Result.ok();
    }

    @Operation(summary = "修改用户状态")
    @PutMapping("/changeStatus")
    @RequiresPermission("system:user:edit")
    public Result<Void> changeStatus(@Valid @RequestBody ChangeStatusDTO dto) {
        userService.changeStatus(dto.getUserId(), dto.getStatus());
        return Result.ok();
    }

    @Operation(summary = "分配用户角色")
    @PutMapping("/assignRoles")
    @RequiresPermission("system:user:edit")
    @Log(title = "用户管理-分配角色", businessType = 2)
    public Result<Void> assignRoles(@Valid @RequestBody UserRoleDTO dto) {
        userService.assignRoles(dto);
        return Result.ok();
    }

    @Operation(summary = "批量导入用户（已关闭）")
    @PostMapping("/import")
    @RequiresPermission("system:user:import")
    @Log(title = "用户管理-批量导入", businessType = 1)
    public Result<UserImportResultVO> importUsers(@RequestParam("file") MultipartFile file) {
        return Result.ok(userService.importUsers(file));
    }

    @Operation(summary = "批量导出用户（已关闭）")
    @PostMapping("/export")
    @RequiresPermission("system:user:export")
    @Log(title = "用户管理-批量导出", businessType = 4)
    public void exportUsers(@RequestBody List<Long> ids, HttpServletResponse response) throws IOException {
        userService.exportUsers(ids, response);
    }

    @Operation(summary = "下载用户导入模板（已关闭）")
    @GetMapping("/import/template")
    @RequiresPermission("system:user:import")
    public void downloadTemplate() {
        throw new BusinessException("已关闭批量导入用户，请通过钉钉扫码登录自动注册");
    }
}
