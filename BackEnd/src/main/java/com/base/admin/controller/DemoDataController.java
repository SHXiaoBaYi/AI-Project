package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.service.DemoYearDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "演示数据")
@RestController
@RequestMapping("/system/demo")
@RequiredArgsConstructor
public class DemoDataController {

    private final DemoYearDataService demoYearDataService;

    @Operation(summary = "刷入2014/2015演示数据")
    @RequiresPermission("system:user:list")
    @PostMapping("/seed")
    public Result<String> seed(@RequestParam(defaultValue = "false") boolean force) {
        return Result.ok(demoYearDataService.seed(force));
    }

    @Operation(summary = "按演示日监测重建周/月/年快照")
    @RequiresPermission("system:user:list")
    @PostMapping("/rebuild-board")
    public Result<String> rebuildBoard() {
        return Result.ok(demoYearDataService.rebuildBoardsOnly());
    }
}
