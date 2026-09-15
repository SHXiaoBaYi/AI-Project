package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoPlatformDTO;
import com.base.admin.domain.dto.GeoPlatformQueryDTO;
import com.base.admin.domain.entity.GeoPlatform;
import com.base.admin.service.GeoPlatformService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "GEO平台", description = "监测平台基础数据")
@RestController
@RequestMapping("/geo/platform")
@RequiredArgsConstructor
public class GeoPlatformController {

    private final GeoPlatformService platformService;

    @Operation(summary = "分页查询平台")
    @PostMapping("/list")
    @RequiresPermission("geo:platform:list")
    public Result<PageResult<GeoPlatform>> list(@RequestBody GeoPlatformQueryDTO query) {
        return Result.ok(platformService.list(query));
    }

    @Operation(summary = "全部平台（下拉，可按类型过滤）")
    @GetMapping("/options")
    public Result<List<GeoPlatform>> options(@RequestParam(required = false) String platformType) {
        if (platformType != null && !platformType.isBlank()) {
            return Result.ok(platformService.listByType(platformType));
        }
        return Result.ok(platformService.listAll());
    }

    @Operation(summary = "新增平台")
    @PostMapping
    @RequiresPermission("geo:platform:add")
    @Log(title = "GEO平台", businessType = 1)
    public Result<Void> create(@Valid @RequestBody GeoPlatformDTO dto) {
        platformService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改平台")
    @PutMapping
    @RequiresPermission("geo:platform:edit")
    @Log(title = "GEO平台", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoPlatformDTO dto) {
        platformService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除平台")
    @DeleteMapping("/{id}")
    @RequiresPermission("geo:platform:delete")
    @Log(title = "GEO平台", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        platformService.delete(id);
        return Result.ok();
    }
}
