package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoArticleSkeletonDTO;
import com.base.admin.domain.dto.GeoArticleSkeletonQueryDTO;
import com.base.admin.domain.entity.GeoArticleSkeleton;
import com.base.admin.service.GeoArticleSkeletonService;
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

@Tag(name = "GEO文章骨架", description = "AI成文章节骨架维护")
@RestController
@RequestMapping("/geo/skeleton")
@RequiredArgsConstructor
public class GeoArticleSkeletonController {

    private final GeoArticleSkeletonService skeletonService;

    @Operation(summary = "分页查询骨架")
    @PostMapping("/list")
    @RequiresPermission("geo:skeleton:list")
    public Result<PageResult<GeoArticleSkeleton>> list(@RequestBody GeoArticleSkeletonQueryDTO query) {
        return Result.ok(skeletonService.list(query));
    }

    @Operation(summary = "骨架详情")
    @GetMapping("/{id:\\d+}")
    @RequiresPermission("geo:skeleton:list")
    public Result<GeoArticleSkeleton> get(@PathVariable Long id) {
        return Result.ok(skeletonService.get(id));
    }

    @Operation(summary = "新增骨架")
    @PostMapping
    @RequiresPermission("geo:skeleton:add")
    @Log(title = "GEO文章骨架", businessType = 1)
    public Result<Map<String, Long>> create(@Valid @RequestBody GeoArticleSkeletonDTO dto) {
        return Result.ok(Map.of("id", skeletonService.create(dto)));
    }

    @Operation(summary = "修改骨架")
    @PutMapping
    @RequiresPermission("geo:skeleton:edit")
    @Log(title = "GEO文章骨架", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoArticleSkeletonDTO dto) {
        skeletonService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除骨架")
    @DeleteMapping("/{id:\\d+}")
    @RequiresPermission("geo:skeleton:delete")
    @Log(title = "GEO文章骨架", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        skeletonService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除骨架")
    @DeleteMapping("/batch")
    @RequiresPermission("geo:skeleton:delete")
    @Log(title = "GEO文章骨架-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        skeletonService.deleteBatch(ids);
        return Result.ok();
    }
}
