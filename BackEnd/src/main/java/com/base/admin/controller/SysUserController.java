package com.base.admin.controller;

import com.alibaba.excel.EasyExcel;
import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.ChangeStatusDTO;
import com.base.admin.domain.dto.ResetPwdDTO;
import com.base.admin.domain.dto.UserDTO;
import com.base.admin.domain.dto.UserExcelRowDTO;
import com.base.admin.domain.dto.UserPageQueryDTO;
import com.base.admin.domain.dto.UserRoleDTO;
import com.base.admin.domain.vo.UserImportResultVO;
import com.base.admin.domain.vo.UserVO;
import com.base.admin.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "用户管理", description = "系统用户增删改查及导入导出接口")
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

    @Operation(summary = "新增用户")
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

    @Operation(summary = "删除用户")
    @DeleteMapping("/{userId}")
    @RequiresPermission("system:user:delete")
    @Log(title = "用户管理", businessType = 3)
    public Result<Void> delete(@PathVariable Long userId) {
        userService.delete(userId);
        return Result.ok();
    }

    @Operation(summary = "批量删除用户")
    @DeleteMapping("/batch")
    @RequiresPermission("system:user:delete")
    @Log(title = "用户管理-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        userService.deleteBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "重置用户密码")
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

    @Operation(summary = "批量导入用户")
    @PostMapping("/import")
    @RequiresPermission("system:user:import")
    @Log(title = "用户管理-批量导入", businessType = 1)
    public Result<UserImportResultVO> importUsers(@RequestParam("file") MultipartFile file) {
        return Result.ok(userService.importUsers(file));
    }

    @Operation(summary = "批量导出用户")
    @PostMapping("/export")
    @RequiresPermission("system:user:export")
    @Log(title = "用户管理-批量导出", businessType = 4)
    public void exportUsers(@RequestBody List<Long> ids, HttpServletResponse response) throws IOException {
        userService.exportUsers(ids, response);
    }

    @Operation(summary = "下载用户导入模板")
    @GetMapping("/import/template")
    @RequiresPermission("system:user:import")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = URLEncoder.encode("用户导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);

        // 示例数据行
        UserExcelRowDTO example = new UserExcelRowDTO();
        example.setUsername("zhangsan");
        example.setPassword("123456");
        example.setNickname("张三");
        example.setEmail("zhangsan@example.com");
        example.setPhone("13800138000");
        example.setGender("男");
        example.setStatus("启用");

        // 填写说明数据（不含表头，表头由 head() 指定）
        List<List<String>> instructions = List.of(
                List.of("用户名", "是", "字母、数字、下划线，不能以数字开头", "zhangsan"),
                List.of("密码", "是", "不少于6位", "123456"),
                List.of("昵称", "否", "任意文本", "张三"),
                List.of("邮箱", "否", "标准邮箱格式，如 xxx@domain.com", "zhangsan@example.com"),
                List.of("手机号", "否", "11位中国大陆手机号，1开头", "13800138000"),
                List.of("性别", "否", "填写：男 / 女，或 1 / 2，不填默认 0", "男"),
                List.of("账户状态", "否", "填写：启用 / 停用，或 0 / 1，不填默认 0", "启用")
        );

        com.alibaba.excel.ExcelWriter writer = EasyExcel.write(response.getOutputStream(), UserExcelRowDTO.class).build();
        writer.write(List.of(example), EasyExcel.writerSheet(0, "用户导入模板").build());
        List<List<String>> head = List.of(
                List.of("字段"), List.of("必填"), List.of("填写规则"), List.of("示例"));
        writer.write(instructions, EasyExcel.writerSheet(1, "填写说明").head(head).build());
        writer.finish();
    }
}
