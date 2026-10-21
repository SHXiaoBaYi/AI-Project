package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoMediaAssetBatchMetaDTO;
import com.base.admin.domain.dto.GeoMediaAssetDTO;
import com.base.admin.domain.dto.GeoMediaAssetQueryDTO;
import com.base.admin.domain.vo.GeoMediaAssetVO;
import com.base.admin.service.GeoMediaAssetService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Tag(name = "GEO素材库", description = "文章插图素材；按用途/标签语义召回，话题可选软优先")
@RestController
@RequestMapping("/geo/media")
@RequiredArgsConstructor
public class GeoMediaAssetController {

    private final GeoMediaAssetService mediaAssetService;

    @Operation(summary = "分页查询素材")
    @PostMapping("/list")
    @RequiresPermission("geo:media:list")
    public Result<PageResult<GeoMediaAssetVO>> list(@RequestBody GeoMediaAssetQueryDTO query) {
        return Result.ok(mediaAssetService.list(query));
    }

    @Operation(summary = "素材详情")
    @GetMapping("/{id:\\d+}")
    @RequiresPermission("geo:media:list")
    public Result<GeoMediaAssetVO> get(@PathVariable Long id) {
        return Result.ok(mediaAssetService.get(id));
    }

    @Operation(summary = "新增素材")
    @PostMapping
    @RequiresPermission("geo:media:add")
    @Log(title = "GEO素材", businessType = 1)
    public Result<Map<String, Long>> create(@Valid @RequestBody GeoMediaAssetDTO dto) {
        Long id = mediaAssetService.create(dto);
        return Result.ok(Map.of("id", id));
    }

    @Operation(summary = "修改素材")
    @PutMapping
    @RequiresPermission("geo:media:edit")
    @Log(title = "GEO素材", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoMediaAssetDTO dto) {
        mediaAssetService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除素材")
    @DeleteMapping("/{id:\\d+}")
    @RequiresPermission("geo:media:delete")
    @Log(title = "GEO素材", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        mediaAssetService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除素材")
    @DeleteMapping("/batch")
    @RequiresPermission("geo:media:delete")
    @Log(title = "GEO素材-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        mediaAssetService.deleteBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "上传单张素材图片")
    @PostMapping("/upload")
    @RequiresPermission("geo:media:add")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String path = mediaAssetService.upload(file);
        return Result.ok(Map.of("fileUrl", path));
    }

    @Operation(summary = "批量上传并入库")
    @PostMapping("/batch-upload")
    @RequiresPermission("geo:media:add")
    @Log(title = "GEO素材-批量上传", businessType = 1)
    public Result<Map<String, Object>> batchUpload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "usageHint", required = false) String usageHint,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "sceneTags", required = false) String sceneTags,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "licenseNote", required = false) String licenseNote) {
        List<Long> ids = mediaAssetService.batchUpload(
                files, usageHint, splitCsv(tags), splitCsv(sceneTags), sourceType, licenseNote);
        return Result.ok(Map.of("ids", ids, "count", ids.size()));
    }

    @Operation(summary = "批量修改用途/标签/话题等")
    @PutMapping("/batch-meta")
    @RequiresPermission("geo:media:edit")
    @Log(title = "GEO素材-批量改用途", businessType = 2)
    public Result<Map<String, Integer>> batchMeta(@Valid @RequestBody GeoMediaAssetBatchMetaDTO dto) {
        int n = mediaAssetService.batchUpdateMeta(dto);
        return Result.ok(Map.of("updated", n));
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
