package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoContentPlacementArticleQueryDTO;
import com.base.admin.domain.dto.GeoContentPlacementCiteDTO;
import com.base.admin.domain.dto.GeoContentPlacementDTO;
import com.base.admin.domain.dto.GeoContentPlacementItemDTO;
import com.base.admin.domain.dto.GeoContentPlacementQueryDTO;
import com.base.admin.domain.dto.GeoGenerateSimilarBatchDTO;
import com.base.admin.domain.dto.GeoGenerateSimilarDTO;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.GeoContentPlacementArticleListVO;
import com.base.admin.domain.vo.GeoContentPlacementCiteVO;
import com.base.admin.domain.vo.GeoContentPlacementDetailVO;
import com.base.admin.domain.vo.GeoContentPlacementItemVO;
import com.base.admin.domain.vo.GeoContentPlacementListVO;
import com.base.admin.domain.vo.GeoImportJobVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.domain.vo.GeoTargetQuestionOptionVO;
import com.base.admin.domain.vo.SysTaskFileVO;
import com.base.admin.common.Constants;
import com.base.admin.domain.entity.GeoPlatform;
import com.base.admin.service.GeoContentPlacementService;
import com.base.admin.service.GeoImportJobService;
import com.base.admin.service.GeoPlatformService;
import com.base.admin.util.GeoExcelTemplateWriter;
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

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "GEO内容投放", description = "内容投放管理")
@RestController
@RequestMapping("/geo/content-placement")
@RequiredArgsConstructor
public class GeoContentPlacementController {

    private final GeoContentPlacementService contentPlacementService;
    private final GeoPlatformService platformService;
    private final GeoImportJobService importJobService;

    @Operation(summary = "分页查询内容投放（按内容聚合）")
    @PostMapping("/list")
    @RequiresPermission({"geo:content:list", "geo:content:work"})
    public Result<PageResult<GeoContentPlacementListVO>> list(@RequestBody GeoContentPlacementQueryDTO query) {
        return Result.ok(contentPlacementService.list(query));
    }

    @Operation(summary = "分页查询已发布文章（投放明细联查主表）")
    @PostMapping("/articles/list")
    @RequiresPermission("geo:article:list")
    public Result<PageResult<GeoContentPlacementArticleListVO>> listArticles(
            @RequestBody GeoContentPlacementArticleQueryDTO query) {
        return Result.ok(contentPlacementService.listArticles(query));
    }

    @Operation(summary = "按话题加载目标问题（新建文章级联）")
    @GetMapping("/target-questions")
    @RequiresPermission({"geo:article:list", "geo:article:add", "geo:content:list", "geo:content:work"})
    public Result<List<GeoTargetQuestionOptionVO>> targetQuestions(@RequestParam Long topicId) {
        return Result.ok(contentPlacementService.listTargetQuestions(topicId));
    }

    @Operation(summary = "内容投放详情")
    @GetMapping("/{id:\\d+}")
    @RequiresPermission({"geo:content:list", "geo:content:work"})
    public Result<GeoContentPlacementDetailVO> detail(@PathVariable Long id) {
        return Result.ok(contentPlacementService.getDetail(id));
    }

    @Operation(summary = "平台发布明细（抽屉）")
    @GetMapping("/{id:\\d+}/items")
    @RequiresPermission({"geo:content:list", "geo:content:work"})
    public Result<List<GeoContentPlacementItemVO>> items(@PathVariable Long id) {
        return Result.ok(contentPlacementService.listItems(id));
    }

    @Operation(summary = "AI引用明细（弹窗，可按平台明细过滤）")
    @GetMapping("/{id:\\d+}/cites")
    @RequiresPermission({"geo:content:list", "geo:content:work", "geo:article:list"})
    public Result<List<GeoContentPlacementCiteVO>> cites(
            @PathVariable Long id,
            @RequestParam(required = false) Long itemId) {
        return Result.ok(contentPlacementService.listCites(id, itemId));
    }

    @Operation(summary = "附件（任务同步）")
    @GetMapping("/{id:\\d+}/proof-files")
    @RequiresPermission({"geo:content:list", "geo:content:work"})
    public Result<List<SysTaskFileVO>> proofFiles(@PathVariable Long id) {
        return Result.ok(contentPlacementService.listProofFiles(id));
    }

    @Operation(summary = "新增内容投放")
    @PostMapping
    @RequiresPermission("geo:content:add")
    @Log(title = "GEO内容投放", businessType = 1)
    public Result<Long> create(@Valid @RequestBody GeoContentPlacementDTO dto) {
        return Result.ok(contentPlacementService.create(dto));
    }

    @Operation(summary = "修改内容投放")
    @PutMapping
    @RequiresPermission("geo:content:edit")
    @Log(title = "GEO内容投放", businessType = 2)
    public Result<Void> update(@Valid @RequestBody GeoContentPlacementDTO dto) {
        contentPlacementService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除内容投放")
    @DeleteMapping("/{id:\\d+}")
    @RequiresPermission("geo:content:delete")
    @Log(title = "GEO内容投放", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        contentPlacementService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除内容投放")
    @DeleteMapping("/batch")
    @RequiresPermission("geo:content:delete")
    @Log(title = "GEO内容投放-批量删除", businessType = 3)
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        contentPlacementService.deleteBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "生成相似问题可用的 AI 厂商")
    @GetMapping("/ai-providers")
    @RequiresPermission("geo:content:generate")
    public Result<List<GeoAiProviderOptionVO>> listAiProviders() {
        return Result.ok(contentPlacementService.listAiProviders());
    }

    @Operation(summary = "生成相似目标问题（AI）")
    @PostMapping("/{id:\\d+}/generate-similar")
    @RequiresPermission("geo:content:generate")
    @Log(title = "GEO内容投放-生成相似问题", businessType = 1)
    public Result<List<GeoContentPlacementListVO>> generateSimilar(
            @PathVariable Long id,
            @RequestBody(required = false) GeoGenerateSimilarDTO dto) {
        String provider = dto == null ? null : dto.getProvider();
        return Result.ok(contentPlacementService.generateSimilar(id, provider));
    }

    @Operation(summary = "批量生成相似目标问题（AI）")
    @PostMapping("/generate-similar/batch")
    @RequiresPermission("geo:content:generate")
    @Log(title = "GEO内容投放-批量生成相似问题", businessType = 1)
    public Result<List<GeoContentPlacementListVO>> generateSimilarBatch(
            @Valid @RequestBody GeoGenerateSimilarBatchDTO dto) {
        return Result.ok(contentPlacementService.generateSimilarBatch(dto.getIds(), dto.getProvider()));
    }

    @Operation(summary = "新增发布详情")
    @PostMapping("/items")
    @RequiresPermission({"geo:content:edit", "geo:content:work", "geo:article:add"})
    @Log(title = "GEO内容投放-发布详情", businessType = 1)
    public Result<Long> createItem(@Valid @RequestBody GeoContentPlacementItemDTO dto) {
        return Result.ok(contentPlacementService.createItem(dto));
    }

    @Operation(summary = "修改发布详情")
    @PutMapping("/items")
    @RequiresPermission({"geo:content:edit", "geo:content:work", "geo:article:edit"})
    @Log(title = "GEO内容投放-发布详情", businessType = 2)
    public Result<Void> updateItem(@Valid @RequestBody GeoContentPlacementItemDTO dto) {
        contentPlacementService.updateItem(dto);
        return Result.ok();
    }

    @Operation(summary = "删除发布详情")
    @DeleteMapping("/items/{itemId}")
    @RequiresPermission({"geo:content:edit", "geo:content:work", "geo:article:delete"})
    @Log(title = "GEO内容投放-发布详情", businessType = 3)
    public Result<Void> deleteItem(@PathVariable Long itemId) {
        contentPlacementService.deleteItem(itemId);
        return Result.ok();
    }

    @Operation(summary = "批量删除发布文章")
    @DeleteMapping("/items/batch")
    @RequiresPermission({"geo:content:edit", "geo:content:work", "geo:article:delete"})
    @Log(title = "GEO内容投放-发布文章批量删除", businessType = 3)
    public Result<Void> deleteItems(@RequestBody List<Long> itemIds) {
        contentPlacementService.deleteItems(itemIds);
        return Result.ok();
    }

    @Operation(summary = "新增AI引用")
    @PostMapping("/cites")
    @RequiresPermission({"geo:content:edit", "geo:content:work", "geo:article:edit"})
    @Log(title = "GEO内容投放-引用", businessType = 1)
    public Result<Long> createCite(@Valid @RequestBody GeoContentPlacementCiteDTO dto) {
        return Result.ok(contentPlacementService.createCite(dto));
    }

    @Operation(summary = "修改AI引用")
    @PutMapping("/cites")
    @RequiresPermission({"geo:content:edit", "geo:content:work", "geo:article:edit"})
    @Log(title = "GEO内容投放-引用", businessType = 2)
    public Result<Void> updateCite(@Valid @RequestBody GeoContentPlacementCiteDTO dto) {
        contentPlacementService.updateCite(dto);
        return Result.ok();
    }

    @Operation(summary = "删除AI引用")
    @DeleteMapping("/cites/{citeId}")
    @RequiresPermission({"geo:content:edit", "geo:content:work", "geo:article:edit"})
    @Log(title = "GEO内容投放-引用", businessType = 3)
    public Result<Void> deleteCite(@PathVariable Long citeId) {
        contentPlacementService.deleteCite(citeId);
        return Result.ok();
    }

    @Operation(summary = "下载内容投放导入模板")
    @GetMapping("/import/template")
    @RequiresPermission("geo:content:import")
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = URLEncoder.encode("内容投放导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        GeoExcelTemplateWriter.writePlacement(response.getOutputStream(),
                platformService.listByType(Constants.PLATFORM_TYPE_AI).stream()
                        .map(GeoPlatform::getPlatformName)
                        .toList());
    }

    @Operation(summary = "导入内容投放 Excel")
    @PostMapping("/import")
    @RequiresPermission("geo:content:import")
    @Log(title = "GEO内容投放", businessType = 1)
    public Result<GeoImportResultVO> importExcel(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.ok(contentPlacementService.importExcel(file.getInputStream()));
    }

    @Operation(summary = "开始导入内容投放（异步，配合进度查询）")
    @PostMapping("/import/start")
    @RequiresPermission("geo:content:import")
    @Log(title = "GEO内容投放", businessType = 1)
    public Result<GeoImportJobVO> startImport(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(importJobService.startPlacement(file.getBytes()));
    }

    @Operation(summary = "查询内容投放导入进度")
    @GetMapping("/import/progress/{jobId}")
    @RequiresPermission("geo:content:import")
    public Result<GeoImportJobVO> importProgress(@PathVariable String jobId) {
        return Result.ok(importJobService.get(jobId));
    }
}
