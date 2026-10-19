package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoContentReviewAnnotationSaveDTO;
import com.base.admin.domain.dto.GeoContentReviewAnnotationUpdateDTO;
import com.base.admin.domain.dto.GeoContentReviewDecideDTO;
import com.base.admin.domain.dto.GeoContentReviewQueryDTO;
import com.base.admin.domain.dto.GeoContentSubmitReviewDTO;
import com.base.admin.domain.dto.GeoContentWritingQueryDTO;
import com.base.admin.domain.vo.GeoContentReviewAnnotationVO;
import com.base.admin.domain.vo.GeoContentReviewCompareVO;
import com.base.admin.domain.vo.GeoContentReviewRowVO;
import com.base.admin.domain.vo.GeoContentWritingRowVO;
import com.base.admin.service.GeoContentWriteService;
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

@Tag(name = "GEO文章撰写与审核")
@RestController
@RequestMapping("/geo/content-write")
@RequiredArgsConstructor
public class GeoContentWriteController {

    private final GeoContentWriteService writeService;

    @Operation(summary = "我的撰写记录")
    @GetMapping("/mine")
    @RequiresPermission("geo:write:mine")
    public Result<List<GeoContentWritingRowVO>> mine(GeoContentWritingQueryDTO query) {
        return Result.ok(writeService.listMyWritings(query));
    }

    @Operation(summary = "待我审核的文章")
    @GetMapping("/pending-review")
    @RequiresPermission("geo:write:review")
    public Result<List<GeoContentReviewRowVO>> pendingReview() {
        return Result.ok(writeService.listPendingReviews());
    }

    @Operation(summary = "审核记录（我发起或参与）")
    @GetMapping("/review-records")
    @RequiresPermission("geo:write:review")
    public Result<List<GeoContentReviewRowVO>> reviewRecords(GeoContentReviewQueryDTO query) {
        return Result.ok(writeService.listReviewRecords(query));
    }

    @Operation(summary = "同一文章各轮审核历史与提审正文快照（对照）")
    @GetMapping("/placement/{placementId:\\d+}/review-compare")
    @RequiresPermission({"geo:write:review", "geo:write:mine"})
    public Result<GeoContentReviewCompareVO> reviewCompare(@PathVariable Long placementId) {
        return Result.ok(writeService.getReviewCompare(placementId));
    }

    @Operation(summary = "撰写人发起两阶段串行审核（每阶段 1 人）")
    @PostMapping("/submit-review")
    @RequiresPermission("geo:write:mine")
    @Log(title = "GEO文章发起审核", businessType = 2)
    public Result<Long> submitReview(@Valid @RequestBody GeoContentSubmitReviewDTO dto) {
        return Result.ok(writeService.submitReview(dto));
    }

    @Operation(summary = "审核人通过或退回")
    @PostMapping("/decide")
    @RequiresPermission("geo:write:review")
    @Log(title = "GEO文章审核决定", businessType = 2)
    public Result<Void> decide(@Valid @RequestBody GeoContentReviewDecideDTO dto) {
        writeService.decide(dto);
        return Result.ok();
    }

    @Operation(summary = "审核人划词/划句/划段批注")
    @PostMapping("/annotation")
    @RequiresPermission("geo:write:review")
    @Log(title = "GEO文章审核划词批注", businessType = 1)
    public Result<GeoContentReviewAnnotationVO> addAnnotation(@Valid @RequestBody GeoContentReviewAnnotationSaveDTO dto) {
        return Result.ok(writeService.addAnnotation(dto));
    }

    @Operation(summary = "修改本人本轮划词批注内容")
    @PutMapping("/annotation")
    @RequiresPermission("geo:write:review")
    @Log(title = "GEO文章审核划词批注", businessType = 2)
    public Result<GeoContentReviewAnnotationVO> updateAnnotation(@Valid @RequestBody GeoContentReviewAnnotationUpdateDTO dto) {
        return Result.ok(writeService.updateAnnotation(dto));
    }

    @Operation(summary = "删除本人本轮划词批注")
    @DeleteMapping("/annotation/{id}")
    @RequiresPermission("geo:write:review")
    @Log(title = "GEO文章审核划词批注", businessType = 3)
    public Result<Void> deleteAnnotation(@PathVariable Long id) {
        writeService.deleteAnnotation(id);
        return Result.ok();
    }
}
