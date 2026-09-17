package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.SysTaskTypeDTO;
import com.base.admin.domain.dto.SysTaskTypeQueryDTO;
import com.base.admin.domain.entity.SysTaskType;
import com.base.admin.service.SysTaskTypeService;
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

@Tag(name = "任务类型", description = "任务类型基础配置")
@RestController
@RequestMapping("/task/type")
@RequiredArgsConstructor
public class SysTaskTypeController {

    private final SysTaskTypeService taskTypeService;

    @Operation(summary = "分页查询任务类型")
    @PostMapping("/list")
    @RequiresPermission("task:type:list")
    public Result<PageResult<SysTaskType>> list(@RequestBody SysTaskTypeQueryDTO query) {
        return Result.ok(taskTypeService.list(query));
    }

    @Operation(summary = "任务类型下拉（任务表单用）")
    @GetMapping("/options")
    @RequiresPermission({"task:type:list", "task:list", "task:mine"})
    public Result<List<SysTaskType>> options() {
        return Result.ok(taskTypeService.listOptions());
    }

    @Operation(summary = "新增任务类型")
    @PostMapping
    @RequiresPermission("task:type:add")
    @Log(title = "任务类型", businessType = 1)
    public Result<Void> create(@Valid @RequestBody SysTaskTypeDTO dto) {
        taskTypeService.create(dto);
        return Result.ok();
    }

    @Operation(summary = "修改任务类型")
    @PutMapping
    @RequiresPermission("task:type:edit")
    @Log(title = "任务类型", businessType = 2)
    public Result<Void> update(@Valid @RequestBody SysTaskTypeDTO dto) {
        taskTypeService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除任务类型")
    @DeleteMapping("/{id}")
    @RequiresPermission("task:type:delete")
    @Log(title = "任务类型", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        taskTypeService.delete(id);
        return Result.ok();
    }
}
