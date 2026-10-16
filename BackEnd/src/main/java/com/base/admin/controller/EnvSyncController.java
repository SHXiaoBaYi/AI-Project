package com.base.admin.controller;

import com.base.admin.common.Result;
import com.base.admin.domain.vo.EnvSyncResultVO;
import com.base.admin.service.EnvSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "测试环境同步", description = "仅测试环境：生产数据→测试；测试表结构→生产")
@RestController
@RequestMapping("/system/env-sync")
@RequiredArgsConstructor
public class EnvSyncController {

    private final EnvSyncService envSyncService;

    @Operation(summary = "同步生产数据到测试（仅数据，不改表结构）")
    @PostMapping("/data-from-prod")
    public Result<EnvSyncResultVO> syncDataFromProd() {
        return Result.ok(envSyncService.syncProdDataToTest());
    }

    @Operation(summary = "同步测试表结构到生产（只增不删）")
    @PostMapping("/schema-to-prod")
    public Result<EnvSyncResultVO> syncSchemaToProd() {
        return Result.ok(envSyncService.syncTestSchemaToProd());
    }
}
