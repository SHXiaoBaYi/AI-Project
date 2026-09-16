package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.SysAiProviderDTO;
import com.base.admin.domain.vo.SysAiProviderVO;
import com.base.admin.service.SysAiProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI 模型配置", description = "系统管理-AI 厂商 API Key 配置")
@RestController
@RequestMapping("/system/ai-provider")
@RequiredArgsConstructor
public class SysAiProviderController {

    private final SysAiProviderService aiProviderService;

    @Operation(summary = "查询 AI 厂商配置列表")
    @GetMapping("/list")
    @RequiresPermission("system:ai:list")
    public Result<List<SysAiProviderVO>> list() {
        return Result.ok(aiProviderService.listAll());
    }

    @Operation(summary = "更新 AI 厂商配置")
    @PutMapping
    @RequiresPermission("system:ai:edit")
    @Log(title = "AI模型配置", businessType = 2)
    public Result<Void> update(@Valid @RequestBody SysAiProviderDTO dto) {
        aiProviderService.update(dto);
        return Result.ok();
    }
}
