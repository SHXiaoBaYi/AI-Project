package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoArticleStylePackDTO;
import com.base.admin.domain.dto.GeoArticleStylePackQueryDTO;
import com.base.admin.domain.entity.GeoArticleStylePack;
import com.base.admin.service.GeoArticleStylePackService;
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

import java.util.List;
import java.util.Map;

@Tag(name = "GEO风格包", description = "AI成文语气/结构/可摘句式维护")
@RestController
@RequestMapping("/geo/style-pack")
@RequiredArgsConstructor
public class GeoArticleStylePackController {

    private final GeoArticleStylePackService stylePackService;

    @Operation(summary = "分页查询风格包")
    @PostMapping("/list")
    @RequiresPermission("geo:stylePack:list")
    public Result<PageResult<GeoArticleStylePack>> list(@RequestBody GeoArticleStylePackQueryDTO query) {
        return Result.ok(stylePackService.list(query));
    }

    @Operation(summary = "风格包详情")
    @GetMapping("/{id:\\d+}")
    @RequiresPermission("geo:stylePack:list")
    public Result<GeoArticleStylePack> get(@PathVariable Long id) {
        return Result.ok(stylePackService.get(id));
    }

    @Operation(summary = "新增风格包")
    @PostMapping
    @RequiresPermission("geo:stylePack:add")
    @Log(title = "GEO风格包", businessType = 1)
    public Result<Map<String, Long>> create(@Valid @RequestBody GeoArticleStylePackDTO dto) {
        return Result.ok(Map.of("id", stylePackService.create(dto)));
    }

    @Operation(summary = "修改风格包")
    @PutMapping
    @RequiresPermission("geo:stylePack:edit")
    @Log(title = "GEO风格包", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoArticleStylePackDTO dto) {
        stylePackService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除风格包")
    @DeleteMapping("/{id:\\d+}")
    @RequiresPermission("geo:stylePack:delete")
    @Log(title = "GEO风格包", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        stylePackService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除风格包")
    @DeleteMapping("/batch")
    @RequiresPermission("geo:stylePack:delete")
    @Log(title = "GEO风格包-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        stylePackService.deleteBatch(ids);
        return Result.ok();
    }
}
