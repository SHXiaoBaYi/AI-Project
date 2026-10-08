package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.HrPipelineBoardQueryDTO;
import com.base.admin.domain.dto.HrPipelineOnboardDTO;
import com.base.admin.domain.dto.HrPipelinePhoneDTO;
import com.base.admin.domain.dto.HrPipelineScreenDTO;
import com.base.admin.domain.vo.HrPipelineBoardVO;
import com.base.admin.domain.vo.HrPipelineDetailVO;
import com.base.admin.service.HrPipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "单岗位简历流程", description = "按岗位跟踪简历筛选、电话沟通、面试与待入职")
@RestController
@RequestMapping("/hr/pipeline")
@RequiredArgsConstructor
public class HrPipelineController {

    private final HrPipelineService pipelineService;

    @Operation(summary = "单岗位流程看板")
    @PostMapping("/board")
    @RequiresPermission("hr:pipeline:list")
    public Result<HrPipelineBoardVO> board(@Valid @RequestBody HrPipelineBoardQueryDTO query) {
        return Result.ok(pipelineService.board(query));
    }

    @Operation(summary = "候选人全流程详情")
    @GetMapping("/detail/{applicationId}")
    @RequiresPermission("hr:pipeline:list")
    public Result<HrPipelineDetailVO> detail(@PathVariable Long applicationId) {
        return Result.ok(pipelineService.detail(applicationId));
    }

    @Operation(summary = "简历初筛")
    @PostMapping("/screen")
    @RequiresPermission("hr:pipeline:edit")
    @Log(title = "简历初筛", businessType = 2)
    public Result<Void> screen(@Valid @RequestBody HrPipelineScreenDTO dto) {
        pipelineService.screen(dto);
        return Result.ok();
    }

    @Operation(summary = "电话沟通")
    @PostMapping("/phone")
    @RequiresPermission("hr:pipeline:edit")
    @Log(title = "电话沟通", businessType = 2)
    public Result<Void> phone(@Valid @RequestBody HrPipelinePhoneDTO dto) {
        pipelineService.phone(dto);
        return Result.ok();
    }

    @Operation(summary = "待入职状态更新")
    @PostMapping("/onboard")
    @RequiresPermission("hr:pipeline:edit")
    @Log(title = "待入职更新", businessType = 2)
    public Result<Void> onboard(@Valid @RequestBody HrPipelineOnboardDTO dto) {
        pipelineService.onboard(dto);
        return Result.ok();
    }
}
