package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoTopicDTO;
import com.base.admin.domain.dto.GeoTopicQueryDTO;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.service.GeoTopicService;
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

@Tag(name = "GEO话题", description = "话题基础数据")
@RestController
@RequestMapping("/geo/topic")
@RequiredArgsConstructor
public class GeoTopicController {

    private final GeoTopicService topicService;

    @Operation(summary = "分页查询话题")
    @PostMapping("/list")
    @RequiresPermission("geo:topic:list")
    public Result<PageResult<GeoTopic>> list(@RequestBody GeoTopicQueryDTO query) {
        return Result.ok(topicService.list(query));
    }

    @Operation(summary = "全部话题（下拉）")
    @GetMapping("/options")
    @RequiresPermission({"geo:topic:list", "board:view", "geo:expose:list", "geo:article:list", "geo:daily:list", "geo:content:list"})
    public Result<List<GeoTopic>> options() {
        return Result.ok(topicService.listAll());
    }

    @Operation(summary = "新增话题")
    @PostMapping
    @RequiresPermission("geo:topic:add")
    @Log(title = "GEO话题", businessType = 1)
    public Result<Void> create(@Valid @RequestBody GeoTopicDTO dto) {
        topicService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改话题")
    @PutMapping
    @RequiresPermission("geo:topic:edit")
    @Log(title = "GEO话题", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoTopicDTO dto) {
        topicService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除话题")
    @DeleteMapping("/{id}")
    @RequiresPermission("geo:topic:delete")
    @Log(title = "GEO话题", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        topicService.delete(id);
        return Result.ok();
    }
}
