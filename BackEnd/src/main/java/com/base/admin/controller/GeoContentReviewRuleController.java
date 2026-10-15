package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoContentReviewRuleDTO;
import com.base.admin.domain.dto.GeoContentReviewRuleQueryDTO;
import com.base.admin.domain.entity.GeoContentReviewRule;
import com.base.admin.service.GeoContentReviewRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "GEO文章审核规则", description = "按撰写人配置审核策略")
@RestController
@RequestMapping("/geo/review-rule")
@RequiredArgsConstructor
public class GeoContentReviewRuleController {

    private final GeoContentReviewRuleService reviewRuleService;

    @Operation(summary = "分页查询审核规则")
    @PostMapping("/list")
    @RequiresPermission("geo:reviewRule:list")
    public Result<PageResult<GeoContentReviewRule>> list(@RequestBody GeoContentReviewRuleQueryDTO query) {
        return Result.ok(reviewRuleService.list(query));
    }

    @Operation(summary = "新增审核规则")
    @PostMapping
    @RequiresPermission("geo:reviewRule:add")
    @Log(title = "GEO文章审核规则", businessType = 1)
    public Result<Void> create(@Valid @RequestBody GeoContentReviewRuleDTO dto) {
        reviewRuleService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改审核规则")
    @PutMapping
    @RequiresPermission("geo:reviewRule:edit")
    @Log(title = "GEO文章审核规则", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoContentReviewRuleDTO dto) {
        reviewRuleService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除审核规则")
    @DeleteMapping("/{id:\\d+}")
    @RequiresPermission("geo:reviewRule:delete")
    @Log(title = "GEO文章审核规则", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        reviewRuleService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除审核规则")
    @DeleteMapping("/batch")
    @RequiresPermission("geo:reviewRule:delete")
    @Log(title = "GEO文章审核规则-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        reviewRuleService.deleteBatch(ids);
        return Result.ok();
    }
}
