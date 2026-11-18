package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.GeoWorkerAccountStatusDTO;
import com.base.admin.domain.dto.GeoWorkerFailDTO;
import com.base.admin.domain.dto.GeoWorkerHeartbeatDTO;
import com.base.admin.domain.vo.GeoWorkerAccountVO;
import com.base.admin.domain.vo.GeoWorkerBoardVO;
import com.base.admin.domain.vo.GeoWorkerClaimVO;
import com.base.admin.service.GeoMonitorWorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "GEO本机采集", description = "Windows 边缘 worker 领取题目、回传网页回答。服务器不启动浏览器。")
@RestController
@RequestMapping("/geo/auto/worker")
@RequiredArgsConstructor
public class GeoMonitorWorkerController {

    private final GeoMonitorWorkerService workerService;

    @Operation(summary = "采集电脑心跳")
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestHeader(value = "X-Geo-Worker-Token", required = false) String token,
                                  @Valid @RequestBody GeoWorkerHeartbeatDTO dto) {
        workerService.requireToken(token);
        workerService.heartbeat(dto);
        return Result.ok();
    }

    @Operation(summary = "领取一题。没有可做的题时 data 为空")
    @PostMapping("/claim")
    public Result<GeoWorkerClaimVO> claim(@RequestHeader(value = "X-Geo-Worker-Token", required = false) String token,
                                          @Valid @RequestBody GeoWorkerHeartbeatDTO dto) {
        workerService.requireToken(token);
        return Result.ok(workerService.claim(dto.getNode()));
    }

    @Operation(summary = "回传回答、引用和整页截图")
    @PostMapping(value = "/result", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> result(@RequestHeader(value = "X-Geo-Worker-Token", required = false) String token,
                               @RequestParam String node,
                               @RequestParam Long jobId,
                               @RequestParam(required = false) String answerText,
                               @RequestParam(required = false) String reasoningText,
                               @RequestParam(required = false) String probeUrl,
                               @RequestParam(required = false) String citationsJson,
                               @RequestParam(required = false) String diagnostic,
                               @RequestPart(required = false) MultipartFile screenshot) {
        workerService.requireToken(token);
        workerService.complete(node, jobId, answerText, reasoningText, probeUrl, citationsJson, diagnostic, screenshot);
        return Result.ok();
    }

    @Operation(summary = "提问失败或遇到登录墙")
    @PostMapping("/fail")
    public Result<Void> fail(@RequestHeader(value = "X-Geo-Worker-Token", required = false) String token,
                             @Valid @RequestBody GeoWorkerFailDTO dto) {
        workerService.requireToken(token);
        workerService.fail(dto);
        return Result.ok();
    }

    @Operation(summary = "这台电脑名下、用于本机 Chrome 的平台账号")
    @PostMapping("/accounts")
    public Result<List<GeoWorkerAccountVO>> accounts(@RequestHeader(value = "X-Geo-Worker-Token", required = false) String token,
                                                     @Valid @RequestBody GeoWorkerHeartbeatDTO dto) {
        workerService.requireToken(token);
        return Result.ok(workerService.accountsFor(dto.getNode()));
    }

    @Operation(summary = "回报某个账号已在本机登录，或需要重新登录")
    @PostMapping("/account-status")
    public Result<Void> accountStatus(@RequestHeader(value = "X-Geo-Worker-Token", required = false) String token,
                                      @Valid @RequestBody GeoWorkerAccountStatusDTO dto) {
        workerService.requireToken(token);
        workerService.reportAccountStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "采集电脑在线状态与排队题数")
    @GetMapping("/board")
    @RequiresPermission("geo:auto:result:list")
    public Result<GeoWorkerBoardVO> board() {
        return Result.ok(workerService.board());
    }
}
