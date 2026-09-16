package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoContentArticleBoardQueryDTO;
import com.base.admin.domain.dto.GeoContentArticleDetailQueryDTO;
import com.base.admin.domain.dto.GeoContentPlacementCiteDTO;
import com.base.admin.domain.dto.GeoContentPlacementDTO;
import com.base.admin.domain.dto.GeoContentPlacementItemDTO;
import com.base.admin.domain.dto.GeoContentPlacementQueryDTO;
import com.base.admin.domain.dto.GeoContentPublisherWeekDetailQueryDTO;
import com.base.admin.domain.dto.GeoContentPublisherWeekQueryDTO;
import com.base.admin.domain.dto.GeoGenerateSimilarDTO;
import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.GeoContentArticleBoardVO;
import com.base.admin.domain.vo.GeoContentArticleDetailRowVO;
import com.base.admin.domain.vo.GeoContentPlacementCiteVO;
import com.base.admin.domain.vo.GeoContentPlacementDetailVO;
import com.base.admin.domain.vo.GeoContentPlacementItemVO;
import com.base.admin.domain.vo.GeoContentPlacementListVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekBoardVO;
import com.base.admin.domain.vo.GeoContentPublisherWeekDetailVO;
import com.base.admin.domain.vo.GeoImportResultVO;
import com.base.admin.service.GeoContentPlacementService;
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

@Tag(name = "GEO内容投放", description = "内容投放管理")
@RestController
@RequestMapping("/geo/content-placement")
@RequiredArgsConstructor
public class GeoContentPlacementController {

    private final GeoContentPlacementService contentPlacementService;

    @Operation(summary = "分页查询内容投放（按内容聚合）")
    @PostMapping("/list")
    @RequiresPermission({"geo:content:list", "geo:content:work"})
    public Result<PageResult<GeoContentPlacementListVO>> list(@RequestBody GeoContentPlacementQueryDTO query) {
        return Result.ok(contentPlacementService.list(query));
    }

    @Operation(summary = "发布人维度周看板")
    @PostMapping("/publisher-weekly-board")
    @RequiresPermission("geo:content:list")
    public Result<GeoContentPublisherWeekBoardVO> publisherWeeklyBoard(@RequestBody GeoContentPublisherWeekQueryDTO query) {
        return Result.ok(contentPlacementService.publisherWeeklyBoard(query));
    }

    @Operation(summary = "发布人周看板指标明细")
    @PostMapping("/publisher-weekly-detail")
    @RequiresPermission("geo:content:list")
    public Result<List<GeoContentPublisherWeekDetailVO>> publisherWeeklyDetail(
            @Valid @RequestBody GeoContentPublisherWeekDetailQueryDTO query) {
        return Result.ok(contentPlacementService.publisherWeeklyDetail(query));
    }

    @Operation(summary = "文章发布/收录看板")
    @PostMapping("/article-board")
    @RequiresPermission({"geo:content:list", "geo:article:list"})
    public Result<GeoContentArticleBoardVO> articleBoard(@RequestBody(required = false) GeoContentArticleBoardQueryDTO query) {
        return Result.ok(contentPlacementService.articlePublishBoard(
                query == null ? new GeoContentArticleBoardQueryDTO() : query));
    }

    @Operation(summary = "文章发布/收录看板下钻明细")
    @PostMapping("/article-board/detail")
    @RequiresPermission({"geo:content:list", "geo:article:list"})
    public Result<List<GeoContentArticleDetailRowVO>> articleBoardDetail(
            @Valid @RequestBody GeoContentArticleDetailQueryDTO query) {
        return Result.ok(contentPlacementService.articlePublishDetail(query));
    }

    @Operation(summary = "内容投放详情")
    @GetMapping("/{id}")
    @RequiresPermission({"geo:content:list", "geo:content:work"})
    public Result<GeoContentPlacementDetailVO> detail(@PathVariable Long id) {
        return Result.ok(contentPlacementService.getDetail(id));
    }

    @Operation(summary = "平台发布明细（抽屉）")
    @GetMapping("/{id}/items")
    @RequiresPermission({"geo:content:list", "geo:content:work"})
    public Result<List<GeoContentPlacementItemVO>> items(@PathVariable Long id) {
        return Result.ok(contentPlacementService.listItems(id));
    }

    @Operation(summary = "AI引用明细（弹窗，可按平台明细过滤）")
    @GetMapping("/{id}/cites")
    @RequiresPermission({"geo:content:list", "geo:content:work"})
    public Result<List<GeoContentPlacementCiteVO>> cites(
            @PathVariable Long id,
            @RequestParam(required = false) Long itemId) {
        return Result.ok(contentPlacementService.listCites(id, itemId));
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
    @DeleteMapping("/{id}")
    @RequiresPermission("geo:content:delete")
    @Log(title = "GEO内容投放", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        contentPlacementService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "生成相似问题可用的 AI 厂商")
    @GetMapping("/ai-providers")
    @RequiresPermission("geo:content:generate")
    public Result<List<GeoAiProviderOptionVO>> listAiProviders() {
        return Result.ok(contentPlacementService.listAiProviders());
    }

    @Operation(summary = "生成相似目标问题（AI）")
    @PostMapping("/{id}/generate-similar")
    @RequiresPermission("geo:content:generate")
    @Log(title = "GEO内容投放-生成相似问题", businessType = 1)
    public Result<List<GeoContentPlacementListVO>> generateSimilar(
            @PathVariable Long id,
            @RequestBody(required = false) GeoGenerateSimilarDTO dto) {
        String provider = dto == null ? null : dto.getProvider();
        return Result.ok(contentPlacementService.generateSimilar(id, provider));
    }

    @Operation(summary = "新增发布详情")
    @PostMapping("/items")
    @RequiresPermission({"geo:content:edit", "geo:content:work"})
    @Log(title = "GEO内容投放-发布详情", businessType = 1)
    public Result<Long> createItem(@Valid @RequestBody GeoContentPlacementItemDTO dto) {
        return Result.ok(contentPlacementService.createItem(dto));
    }

    @Operation(summary = "修改发布详情")
    @PutMapping("/items")
    @RequiresPermission({"geo:content:edit", "geo:content:work"})
    @Log(title = "GEO内容投放-发布详情", businessType = 2)
    public Result<Void> updateItem(@Valid @RequestBody GeoContentPlacementItemDTO dto) {
        contentPlacementService.updateItem(dto);
        return Result.ok();
    }

    @Operation(summary = "删除发布详情")
    @DeleteMapping("/items/{itemId}")
    @RequiresPermission({"geo:content:edit", "geo:content:work"})
    @Log(title = "GEO内容投放-发布详情", businessType = 3)
    public Result<Void> deleteItem(@PathVariable Long itemId) {
        contentPlacementService.deleteItem(itemId);
        return Result.ok();
    }

    @Operation(summary = "新增AI引用")
    @PostMapping("/cites")
    @RequiresPermission({"geo:content:edit", "geo:content:work"})
    @Log(title = "GEO内容投放-引用", businessType = 1)
    public Result<Long> createCite(@Valid @RequestBody GeoContentPlacementCiteDTO dto) {
        return Result.ok(contentPlacementService.createCite(dto));
    }

    @Operation(summary = "修改AI引用")
    @PutMapping("/cites")
    @RequiresPermission({"geo:content:edit", "geo:content:work"})
    @Log(title = "GEO内容投放-引用", businessType = 2)
    public Result<Void> updateCite(@Valid @RequestBody GeoContentPlacementCiteDTO dto) {
        contentPlacementService.updateCite(dto);
        return Result.ok();
    }

    @Operation(summary = "删除AI引用")
    @DeleteMapping("/cites/{citeId}")
    @RequiresPermission({"geo:content:edit", "geo:content:work"})
    @Log(title = "GEO内容投放-引用", businessType = 3)
    public Result<Void> deleteCite(@PathVariable Long citeId) {
        contentPlacementService.deleteCite(citeId);
        return Result.ok();
    }

    @Operation(summary = "导入内容投放 Excel")
    @PostMapping("/import")
    @RequiresPermission("geo:content:import")
    @Log(title = "GEO内容投放", businessType = 1)
    public Result<GeoImportResultVO> importExcel(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.ok(contentPlacementService.importExcel(file.getInputStream()));
    }
}
