package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoPlatformAccountDTO;
import com.base.admin.domain.dto.GeoPlatformAccountQueryDTO;
import com.base.admin.domain.vo.GeoPlatformAccountListVO;
import com.base.admin.service.GeoPlatformAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "GEO平台账号", description = "平台侧运营账号管理")
@RestController
@RequestMapping("/geo/platform-account")
@RequiredArgsConstructor
public class GeoPlatformAccountController {

    private final GeoPlatformAccountService accountService;

    @Operation(summary = "分页查询平台账号")
    @PostMapping("/list")
    @RequiresPermission("geo:platformAccount:list")
    public Result<PageResult<GeoPlatformAccountListVO>> list(@RequestBody GeoPlatformAccountQueryDTO query) {
        return Result.ok(accountService.list(query));
    }

    @Operation(summary = "账号详情")
    @GetMapping("/{id}")
    @RequiresPermission("geo:platformAccount:list")
    public Result<GeoPlatformAccountListVO> getById(@PathVariable Long id) {
        return Result.ok(accountService.getById(id));
    }

    @Operation(summary = "新增平台账号")
    @PostMapping
    @RequiresPermission("geo:platformAccount:add")
    @Log(title = "GEO平台账号", businessType = 1)
    public Result<Void> create(@Valid @RequestBody GeoPlatformAccountDTO dto) {
        accountService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改平台账号")
    @PutMapping
    @RequiresPermission("geo:platformAccount:edit")
    @Log(title = "GEO平台账号", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoPlatformAccountDTO dto) {
        accountService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除平台账号")
    @DeleteMapping("/{id}")
    @RequiresPermission("geo:platformAccount:delete")
    @Log(title = "GEO平台账号", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return Result.ok();
    }
}
