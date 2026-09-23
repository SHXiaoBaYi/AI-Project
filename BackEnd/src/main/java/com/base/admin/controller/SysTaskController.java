package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.SysTaskAssignDTO;
import com.base.admin.domain.dto.SysTaskBatchAssignDTO;
import com.base.admin.domain.dto.SysTaskBatchCompleteDTO;
import com.base.admin.domain.dto.SysTaskBatchDeleteDTO;
import com.base.admin.domain.dto.SysTaskCompleteDTO;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.dto.SysTaskQueryDTO;
import com.base.admin.domain.vo.SysTaskFileVO;
import com.base.admin.domain.vo.SysTaskVO;
import com.base.admin.service.FileStorageService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "任务管理", description = "任务中心")
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class SysTaskController {

    private final SysTaskService taskService;
    private final FileStorageService fileStorageService;

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

    @Operation(summary = "批量分配任务")
    @PostMapping("/assign/batch")
    @RequiresPermission({"task:edit", "task:list"})
    @Log(title = "任务批量分配", businessType = 2)
    public Result<String> batchAssign(@Valid @RequestBody SysTaskBatchAssignDTO dto) {
        return Result.ok(taskService.batchAssign(dto));
    }

    @Operation(summary = "去完成任务（可上传完成证明）")
    @PostMapping("/{id:\\d+}/complete")
    @RequiresPermission({"task:mine", "task:edit", "task:list"})
    @Log(title = "任务完成", businessType = 2)
    public Result<Void> complete(@PathVariable Long id, @RequestBody(required = false) SysTaskCompleteDTO dto) {
        taskService.complete(id, dto == null ? new SysTaskCompleteDTO() : dto);
        return Result.ok();
    }

    @Operation(summary = "批量完成任务（不含需证明附件的类型）")
    @PostMapping("/complete/batch")
    @RequiresPermission({"task:mine", "task:edit", "task:list"})
    @Log(title = "任务批量完成", businessType = 2)
    public Result<String> batchComplete(@Valid @RequestBody SysTaskBatchCompleteDTO dto) {
        return Result.ok(taskService.batchComplete(dto));
    }

    @Operation(summary = "上传任务附件（完成证明）")
    @PostMapping("/file/upload")
    @RequiresPermission({"task:mine", "task:edit", "task:list"})
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        String stored = fileStorageService.saveTaskAttachment(file);
        Map<String, Object> data = new HashMap<>();
        data.put("url", fileStorageService.toPublicUrl(stored));
        data.put("storagePath", stored);
        data.put("fileName", file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        data.put("fileSize", file.getSize());
        data.put("contentType", file.getContentType() == null ? "" : file.getContentType());
        return Result.ok(data);
    }

    @Operation(summary = "任务附件列表")
    @GetMapping("/{id:\\d+}/files")
    @RequiresPermission({"task:list", "task:mine"})
    public Result<List<SysTaskFileVO>> listTaskFiles(@PathVariable Long id) {
        return Result.ok(taskService.listFilesByTaskId(id));
    }

    @Operation(summary = "业务关联附件列表")
    @GetMapping("/files/by-biz")
    @RequiresPermission({"task:list", "task:mine", "geo:content:list", "geo:content:work"})
    public Result<List<SysTaskFileVO>> listBizFiles(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.ok(taskService.listFilesByBiz(bizType, bizId));
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id:\\d+}")
    @RequiresPermission("task:delete")
    @Log(title = "任务管理", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除任务")
    @PostMapping("/delete/batch")
    @RequiresPermission("task:delete")
    @Log(title = "任务批量删除", businessType = 3)
    public Result<String> deleteBatch(@Valid @RequestBody SysTaskBatchDeleteDTO dto) {
        return Result.ok(taskService.deleteBatch(dto));
    }
}
