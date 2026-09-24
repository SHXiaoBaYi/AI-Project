package com.base.admin.controller;

import com.base.admin.annotation.Log;
import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.DingTalkScheduleRecommendDTO;
import com.base.admin.domain.dto.DingTalkScheduleRuleDTO;
import com.base.admin.domain.vo.DingTalkScheduleRecommendVO;
import com.base.admin.domain.vo.DingTalkScheduleRuleVO;
import com.base.admin.service.DingTalkScheduleRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "我的日程规则", description = "仅读写当前登录用户自己的钉钉日程规则；支持秘书推荐预览")
@RestController
@RequestMapping("/system/schedule-rule")
@RequiredArgsConstructor
public class DingTalkScheduleRuleController {

    private final DingTalkScheduleRuleService dingTalkScheduleRuleService;

    @Operation(summary = "获取本人钉钉日程规则")
    @GetMapping("/mine")
    @RequiresPermission("system:schedule-rule:mine")
    public Result<DingTalkScheduleRuleVO> mine() {
        return Result.ok(dingTalkScheduleRuleService.getMine());
    }

    @Operation(summary = "保存本人钉钉日程规则")
    @PutMapping("/mine")
    @RequiresPermission("system:schedule-rule:edit")
    @Log(title = "我的日程规则", businessType = 2)
    public Result<Void> saveMine(@Valid @RequestBody DingTalkScheduleRuleDTO dto) {
        dingTalkScheduleRuleService.saveMine(dto);
        return Result.ok();
    }

    @Operation(summary = "按本人规则+钉钉闲忙推荐可约时段（秘书预览）")
    @PostMapping("/mine/recommend")
    @RequiresPermission("system:schedule-rule:mine")
    public Result<DingTalkScheduleRecommendVO> recommendMine(@RequestBody(required = false) DingTalkScheduleRecommendDTO dto) {
        return Result.ok(dingTalkScheduleRuleService.recommendMine(dto == null ? new DingTalkScheduleRecommendDTO() : dto));
    }
}
