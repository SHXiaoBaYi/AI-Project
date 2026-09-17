package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.SysTaskAssignDTO;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.dto.SysTaskQueryDTO;
import com.base.admin.domain.vo.SysTaskVO;
import com.base.admin.service.SysTaskService;
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

@Tag(name = "任务管理", description = "任务中心")
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class SysTaskController {

    private final SysTaskService taskService;

    @Operation(summary = "分页查询任务")
    @PostMapping("/list")
    @RequiresPermission({"task:list", "task:mine"})
    public Result<PageResult<SysTaskVO>> list(@RequestBody SysTaskQueryDTO query) {
        return Result.ok(taskService.list(query));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id:\\d+}")
    @RequiresPermission({"task:list", "task:mine"})
    public Result<SysTaskVO> getById(@PathVariable Long id) {
        return Result.ok(taskService.getById(id));
    }

    @Operation(summary = "新增任务")
    @PostMapping
    @RequiresPermission("task:add")
    @Log(title = "任务管理", businessType = 1)
    public Result<Long> create(@Valid @RequestBody SysTaskDTO dto) {
        return Result.ok(taskService.create(dto));
    }

    @Operation(summary = "修改任务")
    @PutMapping
    @RequiresPermission("task:edit")
    @Log(title = "任务管理", businessType = 2)
    public Result<Void> update(@Valid @RequestBody SysTaskDTO dto) {
        taskService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "分配任务")
    @PostMapping("/{id:\\d+}/assign")
    @RequiresPermission({"task:edit", "task:list", "task:mine"})
    @Log(title = "任务分配", businessType = 2)
    public Result<Void> assign(@PathVariable Long id, @Valid @RequestBody SysTaskAssignDTO dto) {
        taskService.assign(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id:\\d+}")
    @RequiresPermission("task:delete")
    @Log(title = "任务管理", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.ok();
    }
}
