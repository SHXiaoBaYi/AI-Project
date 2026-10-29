package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.HrKpiAssessmentQueryDTO;
import com.base.admin.domain.dto.HrKpiQueryDTO;
import com.base.admin.domain.dto.HrKpiScoreRuleDTO;
import com.base.admin.domain.dto.HrKpiTaskDueUpdateDTO;
import com.base.admin.domain.vo.HrKpiAssessmentVO;
import com.base.admin.domain.vo.HrKpiBoardVO;
import com.base.admin.domain.vo.HrKpiScoreRuleVO;
import com.base.admin.service.HrKpiService;
import com.base.admin.service.HrKpiTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "招聘KPI看板", description = "岗位分级与招聘人员绩效统计")
@RestController
@RequestMapping("/hr/kpi")
@RequiredArgsConstructor
public class HrKpiController {

    private final HrKpiService kpiService;
    private final HrKpiTaskService kpiTaskService;

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

    @Operation(summary = "考核得分面板（按到期月核算）")
    @PostMapping("/assessment")
    @RequiresPermission("hr:kpi:view")
    public Result<HrKpiAssessmentVO> assessment(@RequestBody(required = false) HrKpiAssessmentQueryDTO query) {
        return Result.ok(kpiTaskService.assessment(query));
    }

    @Operation(summary = "考核任务延期")
    @PutMapping("/task/{id}/due")
    @RequiresPermission("hr:kpi:view")
    public Result<Void> updateDue(@PathVariable Long id, @Valid @RequestBody HrKpiTaskDueUpdateDTO dto) {
        kpiTaskService.updateDueAt(id, dto.getDueAt());
        return Result.ok();
    }

    @Operation(summary = "考核计分规则")
    @GetMapping("/score-rule")
    @RequiresPermission({"hr:kpi-rule:view", "hr:kpi:view"})
    public Result<HrKpiScoreRuleVO> scoreRule() {
        return Result.ok(kpiTaskService.getScoreRule());
    }

    @Operation(summary = "保存考核计分规则")
    @PutMapping("/score-rule")
    @RequiresPermission("hr:kpi-rule:edit")
    public Result<Void> saveScoreRule(@Valid @RequestBody HrKpiScoreRuleDTO dto) {
        kpiTaskService.saveScoreRule(dto);
        return Result.ok();
    }
}
