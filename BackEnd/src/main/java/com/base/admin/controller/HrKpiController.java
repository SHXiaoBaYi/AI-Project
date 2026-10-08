package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.HrKpiQueryDTO;
import com.base.admin.domain.vo.HrKpiBoardVO;
import com.base.admin.service.HrKpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "招聘KPI看板", description = "岗位分级与招聘人员绩效统计")
@RestController
@RequestMapping("/hr/kpi")
@RequiredArgsConstructor
public class HrKpiController {

    private final HrKpiService kpiService;

    @Operation(summary = "KPI 看板数据")
    @PostMapping("/board")
    @RequiresPermission("hr:kpi:view")
    public Result<HrKpiBoardVO> board(@RequestBody(required = false) HrKpiQueryDTO query) {
        return Result.ok(kpiService.board(query));
    }

    @Operation(summary = "导出 KPI 报表")
    @PostMapping("/export")
    @RequiresPermission("hr:kpi:export")
    public void export(@RequestBody(required = false) HrKpiQueryDTO query, HttpServletResponse response) {
        kpiService.export(query, response);
    }
}
