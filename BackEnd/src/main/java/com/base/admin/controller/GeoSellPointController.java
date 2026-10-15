package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoSellDomainDTO;
import com.base.admin.domain.dto.GeoSellPointDTO;
import com.base.admin.domain.dto.GeoSellPointPreviewDTO;
import com.base.admin.domain.dto.GeoSellPointQueryDTO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoSellDomainVO;
import com.base.admin.domain.vo.GeoSellPointContextVO;
import com.base.admin.domain.vo.GeoSellPointVO;
import com.base.admin.service.GeoSellPointService;
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

import java.util.List;

@Tag(name = "GEO卖点库", description = "卖点域树与条目维护，供 AI 生成/审核引用")
@RestController
@RequestMapping("/geo/sell-point")
@RequiredArgsConstructor
public class GeoSellPointController {

    private final GeoSellPointService sellPointService;

    @Operation(summary = "卖点域树")
    @GetMapping("/domains/tree")
    @RequiresPermission("geo:sellPoint:list")
    public Result<List<GeoSellDomainVO>> domainTree() {
        return Result.ok(sellPointService.domainTree());
    }

    @Operation(summary = "新增卖点域")
    @PostMapping("/domains")
    @RequiresPermission("geo:sellPoint:add")
    @Log(title = "GEO卖点域", businessType = 1)
    public Result<Void> createDomain(@Valid @RequestBody GeoSellDomainDTO dto) {
        sellPointService.createDomain(dto);
        return Result.ok();
    }

    @Operation(summary = "修改卖点域")
    @PutMapping("/domains")
    @RequiresPermission("geo:sellPoint:edit")
    @Log(title = "GEO卖点域", businessType = 2)
    public Result<Void> updateDomain(@Valid @RequestBody GeoSellDomainDTO dto) {
        sellPointService.updateDomain(dto);
        return Result.ok();
    }

    @Operation(summary = "删除卖点域")
    @DeleteMapping("/domains/{id:\\d+}")
    @RequiresPermission("geo:sellPoint:delete")
    @Log(title = "GEO卖点域", businessType = 3)
    public Result<Void> deleteDomain(@PathVariable Long id) {
        sellPointService.deleteDomain(id);
        return Result.ok();
    }

    @Operation(summary = "分页查询卖点条目")
    @PostMapping("/list")
    @RequiresPermission("geo:sellPoint:list")
    public Result<PageResult<GeoSellPointVO>> list(@RequestBody GeoSellPointQueryDTO query) {
        return Result.ok(sellPointService.listPoints(query));
    }

    @Operation(summary = "新增卖点条目")
    @PostMapping
    @RequiresPermission("geo:sellPoint:add")
    @Log(title = "GEO卖点条目", businessType = 1)
    public Result<Void> create(@Valid @RequestBody GeoSellPointDTO dto) {
        sellPointService.createPoint(dto);
        return Result.ok();
    }

    @Operation(summary = "修改卖点条目")
    @PutMapping
    @RequiresPermission("geo:sellPoint:edit")
    @Log(title = "GEO卖点条目", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoSellPointDTO dto) {
        sellPointService.updatePoint(dto);
        return Result.ok();
    }

    @Operation(summary = "删除卖点条目")
    @DeleteMapping("/{id:\\d+}")
    @RequiresPermission("geo:sellPoint:delete")
    @Log(title = "GEO卖点条目", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        sellPointService.deletePoint(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除卖点条目")
    @DeleteMapping("/batch")
    @RequiresPermission("geo:sellPoint:delete")
    @Log(title = "GEO卖点条目-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        sellPointService.deletePointBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "预览 AI 召回上下文")
    @PostMapping("/preview-context")
    @RequiresPermission("geo:sellPoint:list")
    public Result<GeoSellPointContextVO> preview(@RequestBody GeoSellPointPreviewDTO dto) {
        return Result.ok(sellPointService.preview(dto));
    }

    @Operation(summary = "上传 Excel 导入卖点")
    @PostMapping("/import")
    @RequiresPermission("geo:sellPoint:import")
    @Log(title = "GEO卖点库-导入", businessType = 1)
    public Result<GeoImportResultVO> importExcel(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.ok(sellPointService.importExcel(file.getInputStream()));
    }

    @Operation(summary = "从项目 docs/小巴依卖点.xlsx 一键初始化")
    @PostMapping("/import-default")
    @RequiresPermission("geo:sellPoint:import")
    @Log(title = "GEO卖点库-默认导入", businessType = 1)
    public Result<GeoImportResultVO> importDefault() {
        return Result.ok(sellPointService.importFromProjectDocs());
    }
}
