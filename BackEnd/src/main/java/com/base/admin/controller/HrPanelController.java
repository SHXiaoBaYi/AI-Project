package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.HrPanelDetailQueryDTO;
import com.base.admin.domain.dto.HrPanelQueryDTO;
import com.base.admin.domain.vo.HrPanelDetailVO;
import com.base.admin.domain.vo.HrPanelVO;
import com.base.admin.service.HrPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "招聘面板", description = "在招岗位统计与岗位进展")
@RestController
@RequestMapping("/hr/panel")
@RequiredArgsConstructor
public class HrPanelController {

    private final HrPanelService panelService;

    @Operation(summary = "招聘面板统计与分页列表")
    @PostMapping
    @RequiresPermission("hr:panel:view")
    public Result<HrPanelVO> panel(@RequestBody(required = false) HrPanelQueryDTO query) {
        return Result.ok(panelService.panel(query));
    }

    @Operation(summary = "招聘面板指标明细")
    @PostMapping("/detail")
    @RequiresPermission("hr:panel:view")
    public Result<HrPanelDetailVO> detail(@Valid @RequestBody HrPanelDetailQueryDTO query) {
        return Result.ok(panelService.detail(query));
    }
}
