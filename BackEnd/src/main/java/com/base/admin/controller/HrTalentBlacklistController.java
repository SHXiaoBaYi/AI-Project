package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.HrTalentBlacklistLogQueryDTO;
import com.base.admin.domain.dto.HrTalentBlacklistMoveDTO;
import com.base.admin.domain.dto.HrTalentBlacklistQueryDTO;
import com.base.admin.domain.dto.HrTalentBlacklistRestoreDTO;
import com.base.admin.domain.vo.HrTalentBlacklistLogVO;
import com.base.admin.domain.vo.HrTalentBlacklistVO;
import com.base.admin.service.HrTalentBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "黑名单人才库")
@RestController
@RequestMapping("/hr/blacklist")
@RequiredArgsConstructor
public class HrTalentBlacklistController {

    private final HrTalentBlacklistService blacklistService;

    @Operation(summary = "黑名单人才列表")
    @PostMapping("/list")
    @RequiresPermission("hr:blacklist:list")
    public Result<List<HrTalentBlacklistVO>> list(@RequestBody(required = false) HrTalentBlacklistQueryDTO query) {
        String keyword = query == null ? null : query.getKeyword();
        return Result.ok(blacklistService.list(keyword));
    }

    @Operation(summary = "批量移入黑名单")
    @PostMapping("/in")
    @RequiresPermission("hr:blacklist:in")
    @Log(title = "移入黑名单", businessType = 2)
    public Result<Void> moveIn(@Valid @RequestBody HrTalentBlacklistMoveDTO dto) {
        blacklistService.moveIn(dto);
        return Result.ok();
    }

    @Operation(summary = "移回正常人才库")
    @PostMapping("/out")
    @RequiresPermission("hr:blacklist:out")
    @Log(title = "移出黑名单", businessType = 2)
    public Result<Void> moveOut(@Valid @RequestBody HrTalentBlacklistRestoreDTO dto) {
        blacklistService.moveOut(dto);
        return Result.ok();
    }

    @Operation(summary = "移入移出记录")
    @PostMapping("/log/list")
    @RequiresPermission("hr:blacklist:log")
    public Result<PageResult<HrTalentBlacklistLogVO>> logs(
            @RequestBody(required = false) HrTalentBlacklistLogQueryDTO query) {
        return Result.ok(blacklistService.logs(query));
    }
}
