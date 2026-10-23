package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoReviewKbDTO;
import com.base.admin.domain.dto.GeoReviewKbQueryDTO;
import com.base.admin.domain.entity.GeoReviewKb;
import com.base.admin.service.GeoReviewKbService;
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
import java.util.Map;

@Tag(name = "GEO内容审核知识库")
@RestController
@RequestMapping("/geo/review-kb")
@RequiredArgsConstructor
public class GeoReviewKbController {

    private final GeoReviewKbService kbService;

    @Operation(summary = "分类列表")
    @GetMapping("/categories")
    @RequiresPermission("geo:reviewKb:list")
    public Result<List<Map<String, Object>>> categories() {
        return Result.ok(kbService.categories());
    }

    @Operation(summary = "分页查询")
    @PostMapping("/list")
    @RequiresPermission("geo:reviewKb:list")
    public Result<PageResult<GeoReviewKb>> list(@RequestBody GeoReviewKbQueryDTO query) {
        return Result.ok(kbService.list(query));
    }

    @Operation(summary = "新增")
    @PostMapping
    @RequiresPermission("geo:reviewKb:add")
    @Log(title = "GEO审稿知识库", businessType = 1)
    public Result<Void> create(@Valid @RequestBody GeoReviewKbDTO dto) {
        kbService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改")
    @PutMapping
    @RequiresPermission("geo:reviewKb:edit")
    @Log(title = "GEO审稿知识库", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoReviewKbDTO dto) {
        kbService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    @RequiresPermission("geo:reviewKb:delete")
    @Log(title = "GEO审稿知识库", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        kbService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除")
    @DeleteMapping("/batch")
    @RequiresPermission("geo:reviewKb:delete")
    @Log(title = "GEO审稿知识库-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        kbService.deleteBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "从系统种子初始化/覆盖（force=true 覆盖同 code 正文）")
    @PostMapping("/seed")
    @RequiresPermission("geo:reviewKb:edit")
    @Log(title = "GEO审稿知识库-种子", businessType = 2)
    public Result<Integer> seed(@RequestParam(defaultValue = "false") boolean force) {
        return Result.ok(kbService.seedDefaults(force));
    }
}
