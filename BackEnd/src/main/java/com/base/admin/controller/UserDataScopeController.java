package com.base.admin.controller;

import com.base.admin.common.Result;
import com.base.admin.domain.dto.UserDataScopeSaveDTO;
import com.base.admin.domain.vo.UserDataScopeMetaVO;
import com.base.admin.domain.vo.UserDataScopeVO;
import com.base.admin.service.UserDataScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "数据权限配置", description = "暗门页：localhost 或 bella 可访问；按模块配置可见数据切片")
@RestController
@RequestMapping("/system/data-scope")
@RequiredArgsConstructor
public class UserDataScopeController {

    private final UserDataScopeService dataScopeService;

    @Operation(summary = "当前用户是否可进入数据权限配置")
    @GetMapping("/access")
    public Result<Map<String, Boolean>> access() {
        return Result.ok(Map.of("allowed", dataScopeService.currentUserCanAccess()));
    }

    @Operation(summary = "配置页下拉元数据（话题/平台/部门/任务类型）")
    @GetMapping("/meta")
    public Result<UserDataScopeMetaVO> meta() {
        return Result.ok(dataScopeService.meta());
    }

    @Operation(summary = "可配置的用户列表")
    @GetMapping("/users")
    public Result<List<UserDataScopeVO>> users() {
        return Result.ok(dataScopeService.listUsers());
    }

    @Operation(summary = "查询某用户的数据权限配置")
    @GetMapping("/{userId}")
    public Result<UserDataScopeVO> get(@PathVariable Long userId) {
        return Result.ok(dataScopeService.get(userId));
    }

    @Operation(summary = "保存用户数据权限配置")
    @PutMapping
    public Result<Void> save(@Valid @RequestBody UserDataScopeSaveDTO dto) {
        dataScopeService.save(dto);
        return Result.ok();
    }
}
