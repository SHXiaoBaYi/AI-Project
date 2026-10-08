package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.TrainQaCalibrateQueryDTO;
import com.base.admin.domain.dto.TrainQaCalibrateSaveDTO;
import com.base.admin.domain.vo.TrainQaCalibrateVO;
import com.base.admin.service.TrainQaCalibrateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "培训答疑校准", description = "人工校准准/不准答案维护")
@RestController
@RequestMapping("/train/qa")
@RequiredArgsConstructor
public class TrainQaCalibrateController {

    private final TrainQaCalibrateService trainQaCalibrateService;

    @Operation(summary = "校准问答分页")
    @GetMapping("/list")
    @RequiresPermission("train:qa:list")
    public Result<PageResult<TrainQaCalibrateVO>> list(TrainQaCalibrateQueryDTO query) {
        return Result.ok(trainQaCalibrateService.page(query));
    }

    @Operation(summary = "新增/编辑校准答案")
    @PostMapping
    @RequiresPermission("train:qa:edit")
    public Result<Long> save(@Valid @RequestBody TrainQaCalibrateSaveDTO dto) {
        return Result.ok(trainQaCalibrateService.save(dto));
    }

    @Operation(summary = "删除校准记录")
    @DeleteMapping("/{id}")
    @RequiresPermission("train:qa:edit")
    public Result<Void> delete(@PathVariable Long id) {
        trainQaCalibrateService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "对不准答案重新发起文档识别")
    @PostMapping("/{id}/rerecognize")
    @RequiresPermission("train:qa:recognize")
    public Result<TrainQaCalibrateVO> rerecognize(@PathVariable Long id) {
        return Result.ok(trainQaCalibrateService.rerecognize(id));
    }
}
