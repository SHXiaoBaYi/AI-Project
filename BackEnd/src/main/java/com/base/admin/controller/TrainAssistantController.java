package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.TrainAssistantAskDTO;
import com.base.admin.domain.dto.TrainQaFeedbackDTO;
import com.base.admin.domain.vo.TrainAssistantAskVO;
import com.base.admin.domain.vo.TrainAssistantEntryVO;
import com.base.admin.domain.vo.TrainAssistantMetaVO;
import com.base.admin.service.DingTalkJsapiService;
import com.base.admin.service.TrainDocService;
import com.base.admin.service.TrainQaCalibrateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "收银培训答疑助手", description = "钉钉 H5 满屏答疑聊天")
@RestController
@RequestMapping("/train/assistant")
@RequiredArgsConstructor
public class TrainAssistantController {

    private final TrainDocService trainDocService;
    private final TrainQaCalibrateService trainQaCalibrateService;
    private final DingTalkJsapiService dingTalkJsapiService;

    @Operation(summary = "长期有效扫码入口（管理端展示二维码）")
    @GetMapping("/entry")
    @RequiresPermission("train:doc:list")
    public Result<TrainAssistantEntryVO> entry() {
        return Result.ok(trainDocService.assistantEntry());
    }

    @Operation(summary = "钉钉 JSAPI 鉴权（语音等）")
    @GetMapping("/jsapi-config")
    public Result<Map<String, Object>> jsapiConfig(@RequestParam String url) {
        return Result.ok(dingTalkJsapiService.sign(url));
    }

    @Operation(summary = "助手元信息")
    @GetMapping("/meta")
    public Result<TrainAssistantMetaVO> meta(@RequestParam(required = false) String category) {
        return Result.ok(trainDocService.assistantMeta(category));
    }

    @Operation(summary = "提问答疑")
    @PostMapping("/ask")
    public Result<TrainAssistantAskVO> ask(@Valid @RequestBody TrainAssistantAskDTO dto) {
        return Result.ok(trainDocService.ask(dto));
    }

    @Operation(summary = "标记本次答案准/不准（准则入库优先命中）")
    @PostMapping("/feedback")
    public Result<Long> feedback(@Valid @RequestBody TrainQaFeedbackDTO dto) {
        return Result.ok(trainQaCalibrateService.feedback(dto));
    }
}
