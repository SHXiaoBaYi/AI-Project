package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoAutoQuestionDTO;
import com.base.admin.domain.dto.GeoAutoQuestionEnabledDTO;
import com.base.admin.domain.dto.GeoAutoQuestionQueryDTO;
import com.base.admin.domain.dto.GeoAutoResultQueryDTO;
import com.base.admin.domain.dto.GeoAutoTaskDTO;
import com.base.admin.domain.dto.GeoAutoTaskQueryDTO;
import com.base.admin.domain.entity.GeoMonitorRun;
import com.base.admin.domain.vo.GeoAutoPlatformOptionVO;
import com.base.admin.domain.vo.GeoAutoQuestionOptionVO;
import com.base.admin.domain.vo.GeoAutoQuestionVO;
import com.base.admin.domain.vo.GeoAutoResultDetailVO;
import com.base.admin.domain.vo.GeoAutoResultMatrixVO;
import com.base.admin.domain.vo.GeoAutoResultVO;
import com.base.admin.domain.vo.GeoAutoSeedResultVO;
import com.base.admin.domain.vo.GeoAutoTaskVO;
import com.base.admin.service.GeoAutoMonitorService;
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

import java.util.List;

@Tag(name = "GEO自动监测", description = "监控问题库、监测任务与问题监控分析（结果自动回写日监测）")
@RestController
@RequestMapping("/geo/auto")
@RequiredArgsConstructor
public class GeoAutoMonitorController {

    private final GeoAutoMonitorService autoMonitorService;

    // ==================== 监控问题库 ====================

    @Operation(summary = "监控问题分页")
    @PostMapping("/questions/list")
    @RequiresPermission("geo:auto:question:list")
    public Result<PageResult<GeoAutoQuestionVO>> listQuestions(@RequestBody GeoAutoQuestionQueryDTO query) {
        return Result.ok(autoMonitorService.listQuestions(query));
    }

    @Operation(summary = "监控问题下拉（任务圈选用，仅启用）")
    @GetMapping("/questions/options")
    @RequiresPermission({"geo:auto:question:list", "geo:auto:task:list"})
    public Result<List<GeoAutoQuestionOptionVO>> questionOptions(@RequestParam(required = false) Long topicId) {
        return Result.ok(autoMonitorService.questionOptions(topicId));
    }

    @Operation(summary = "新增监控问题")
    @PostMapping("/questions")
    @RequiresPermission("geo:auto:question:add")
    @Log(title = "GEO监控问题", businessType = 1)
    public Result<Void> createQuestion(@Valid @RequestBody GeoAutoQuestionDTO dto) {
        autoMonitorService.createQuestion(dto);
        return Result.ok();
    }

    @Operation(summary = "修改监控问题")
    @PutMapping("/questions")
    @RequiresPermission("geo:auto:question:edit")
    @Log(title = "GEO监控问题", businessType = 2)
    public Result<Void> updateQuestion(@Valid @RequestBody GeoAutoQuestionDTO dto) {
        autoMonitorService.updateQuestion(dto);
        return Result.ok();
    }

    @Operation(summary = "删除监控问题（有历史结果时不允许）")
    @DeleteMapping("/questions/{id:\\d+}")
    @RequiresPermission("geo:auto:question:delete")
    @Log(title = "GEO监控问题", businessType = 3)
    public Result<Void> deleteQuestion(@PathVariable Long id) {
        autoMonitorService.deleteQuestion(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除监控问题")
    @DeleteMapping("/questions/batch")
    @RequiresPermission("geo:auto:question:delete")
    @Log(title = "GEO监控问题-批量删除", businessType = 3)
    public Result<Void> deleteQuestionBatch(@RequestBody List<Long> ids) {
        autoMonitorService.deleteQuestionBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "批量启用/停用监控问题")
    @PostMapping("/questions/enabled")
    @RequiresPermission("geo:auto:question:edit")
    @Log(title = "GEO监控问题-批量启停", businessType = 2)
    public Result<Void> setQuestionsEnabled(@Valid @RequestBody GeoAutoQuestionEnabledDTO dto) {
        autoMonitorService.setQuestionsEnabled(dto.getIds(), dto.getEnabled());
        return Result.ok();
    }

    @Operation(summary = "从历史数据洗入问题库（日监测关键字 + 内容投放目标问题）")
    @PostMapping("/questions/seed")
    @RequiresPermission("geo:auto:question:add")
    @Log(title = "GEO监控问题-洗入", businessType = 1)
    public Result<GeoAutoSeedResultVO> seedQuestions() {
        return Result.ok(autoMonitorService.seedQuestions());
    }

    // ==================== 监测任务 ====================

    @Operation(summary = "监测任务分页")
    @PostMapping("/tasks/list")
    @RequiresPermission("geo:auto:task:list")
    public Result<PageResult<GeoAutoTaskVO>> listTasks(@RequestBody GeoAutoTaskQueryDTO query) {
        return Result.ok(autoMonitorService.listTasks(query));
    }

    @Operation(summary = "可监测平台下拉（AI平台 + 厂商就绪状态）")
    @GetMapping("/platforms/options")
    @RequiresPermission({"geo:auto:task:list", "geo:auto:question:list"})
    public Result<List<GeoAutoPlatformOptionVO>> platformOptions() {
        return Result.ok(autoMonitorService.platformOptions());
    }

    @Operation(summary = "新增监测任务")
    @PostMapping("/tasks")
    @RequiresPermission("geo:auto:task:add")
    @Log(title = "GEO监测任务", businessType = 1)
    public Result<Void> createTask(@Valid @RequestBody GeoAutoTaskDTO dto) {
        autoMonitorService.createTask(dto);
        return Result.ok();
    }

    @Operation(summary = "修改监测任务")
    @PutMapping("/tasks")
    @RequiresPermission("geo:auto:task:edit")
    @Log(title = "GEO监测任务", businessType = 2)
    public Result<Void> updateTask(@Valid @RequestBody GeoAutoTaskDTO dto) {
        autoMonitorService.updateTask(dto);
        return Result.ok();
    }

    @Operation(summary = "删除监测任务")
    @DeleteMapping("/tasks/{id:\\d+}")
    @RequiresPermission("geo:auto:task:delete")
    @Log(title = "GEO监测任务", businessType = 3)
    public Result<Void> deleteTask(@PathVariable Long id) {
        autoMonitorService.deleteTask(id);
        return Result.ok();
    }

    @Operation(summary = "启用/停用监测任务")
    @PostMapping("/tasks/{id:\\d+}/enabled/{enabled:\\d}")
    @RequiresPermission("geo:auto:task:edit")
    @Log(title = "GEO监测任务-启停", businessType = 2)
    public Result<Void> toggleTask(@PathVariable Long id, @PathVariable Integer enabled) {
        autoMonitorService.toggleTask(id, enabled);
        return Result.ok();
    }

    @Operation(summary = "立即运行（手动触发）")
    @PostMapping("/tasks/{id:\\d+}/run")
    @RequiresPermission("geo:auto:task:run")
    @Log(title = "GEO监测任务-运行", businessType = 1)
    public Result<GeoMonitorRun> runTask(@PathVariable Long id) {
        return Result.ok(autoMonitorService.startTaskRun(id));
    }

    // ==================== 运行批次与结果 ====================

    @Operation(summary = "运行批次分页（进度查询）")
    @GetMapping("/runs")
    @RequiresPermission({"geo:auto:task:list", "geo:auto:result:list"})
    public Result<PageResult<GeoMonitorRun>> listRuns(@RequestParam(required = false) Long taskId,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(autoMonitorService.listRuns(taskId, pageNum, pageSize));
    }

    @Operation(summary = "运行批次详情")
    @GetMapping("/runs/{id:\\d+}")
    @RequiresPermission({"geo:auto:task:list", "geo:auto:result:list"})
    public Result<GeoMonitorRun> getRun(@PathVariable Long id) {
        return Result.ok(autoMonitorService.getRun(id));
    }

    @Operation(summary = "监测结果分页（明细视图）")
    @PostMapping("/results/list")
    @RequiresPermission("geo:auto:result:list")
    public Result<PageResult<GeoAutoResultVO>> listResults(@RequestBody GeoAutoResultQueryDTO query) {
        return Result.ok(autoMonitorService.listResults(query));
    }

    @Operation(summary = "监测结果详情（回答原文/引用/与上次对比）")
    @GetMapping("/results/{id:\\d+}")
    @RequiresPermission("geo:auto:result:list")
    public Result<GeoAutoResultDetailVO> getResultDetail(@PathVariable Long id) {
        return Result.ok(autoMonitorService.getResultDetail(id));
    }

    @Operation(summary = "问题×平台矩阵视图（格子=区间内最新监测）")
    @PostMapping("/results/matrix")
    @RequiresPermission("geo:auto:result:list")
    public Result<GeoAutoResultMatrixVO> resultMatrix(@RequestBody(required = false) GeoAutoResultQueryDTO query) {
        return Result.ok(autoMonitorService.resultMatrix(query == null ? new GeoAutoResultQueryDTO() : query));
    }
}
