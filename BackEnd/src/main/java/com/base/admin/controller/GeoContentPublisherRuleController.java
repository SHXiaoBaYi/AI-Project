package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoContentPublisherRuleDTO;
import com.base.admin.domain.dto.GeoContentPublisherRuleQueryDTO;
import com.base.admin.domain.entity.GeoContentPublisherRule;
import com.base.admin.service.GeoContentPublisherRuleService;
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

@Tag(name = "GEO文章发布人规则", description = "配置默认发布人（未指定时自动套用）")
@RestController
@RequestMapping("/geo/publisher-rule")
@RequiredArgsConstructor
public class GeoContentPublisherRuleController {

    private final GeoContentPublisherRuleService publisherRuleService;

    @Operation(summary = "分页查询发布人规则")
    @PostMapping("/list")
    @RequiresPermission("geo:publisherRule:list")
    public Result<PageResult<GeoContentPublisherRule>> list(@RequestBody GeoContentPublisherRuleQueryDTO query) {
        return Result.ok(publisherRuleService.list(query));
    }

    @Operation(summary = "当前默认发布人（新增目标问题预填；投放管理可读）")
    @GetMapping("/default")
    @RequiresPermission({"geo:publisherRule:list", "geo:content:list", "geo:content:add"})
    public Result<GeoContentPublisherRule> currentDefault() {
        return Result.ok(publisherRuleService.getDefaultForForm().orElse(null));
    }

    @Operation(summary = "新增发布人规则")
    @PostMapping
    @RequiresPermission("geo:publisherRule:add")
    @Log(title = "GEO文章发布人规则", businessType = 1)
    public Result<Void> create(@Valid @RequestBody GeoContentPublisherRuleDTO dto) {
        publisherRuleService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改发布人规则")
    @PutMapping
    @RequiresPermission("geo:publisherRule:edit")
    @Log(title = "GEO文章发布人规则", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoContentPublisherRuleDTO dto) {
        publisherRuleService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除发布人规则")
    @DeleteMapping("/{id:\\d+}")
    @RequiresPermission("geo:publisherRule:delete")
    @Log(title = "GEO文章发布人规则", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        publisherRuleService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除发布人规则")
    @DeleteMapping("/batch")
    @RequiresPermission("geo:publisherRule:delete")
    @Log(title = "GEO文章发布人规则-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        publisherRuleService.deleteBatch(ids);
        return Result.ok();
    }
}
