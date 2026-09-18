package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.BoardChartDrillQueryDTO;
import com.base.admin.domain.dto.BoardTaskOpsDrillQueryDTO;
import com.base.admin.domain.dto.BoardTaskOpsQueryDTO;
import com.base.admin.domain.dto.BoardTaskTofuQueryDTO;
import com.base.admin.domain.vo.BoardChartDrillVO;
import com.base.admin.domain.vo.BoardTaskOpsPersonRateVO;
import com.base.admin.domain.vo.BoardTaskOpsRowVO;
import com.base.admin.domain.vo.BoardTaskOpsSummaryVO;
import com.base.admin.domain.vo.BoardTaskTofuChartVO;
import com.base.admin.domain.vo.GeoContentArticleDetailRowVO;
import com.base.admin.service.BoardChartDrillService;
import com.base.admin.service.BoardTaskOpsService;
import com.base.admin.service.BoardTaskTofuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "统一数据看板", description = "GEO/员工收录/任务聚合看板")
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardChartDrillService boardChartDrillService;
    private final BoardTaskTofuService boardTaskTofuService;
    private final BoardTaskOpsService boardTaskOpsService;

    @Operation(summary = "员工收录看板豆腐块图表（发布/收录）")
    @PostMapping("/task/tofu-chart")
    @RequiresPermission({"board:view", "board:task", "task:list", "geo:content:list", "geo:article:list"})
    public Result<BoardTaskTofuChartVO> taskTofuChart(@RequestBody(required = false) BoardTaskTofuQueryDTO query) {
        return Result.ok(boardTaskTofuService.chart(query == null ? new BoardTaskTofuQueryDTO() : query));
    }

    @Operation(summary = "员工收录看板发布明细抽屉")
    @PostMapping("/task/tofu-publish-detail")
    @RequiresPermission({"board:view", "board:task", "task:list", "geo:content:list", "geo:article:list"})
    public Result<List<GeoContentArticleDetailRowVO>> taskTofuPublishDetail(
            @RequestBody(required = false) BoardTaskTofuQueryDTO query) {
        return Result.ok(boardTaskTofuService.publishDetail(query == null ? new BoardTaskTofuQueryDTO() : query));
    }

    @Operation(summary = "任务看板汇总（到期/完成/完成率）")
    @PostMapping("/work/summary")
    @RequiresPermission({"board:view", "board:task", "task:list", "task:mine"})
    public Result<BoardTaskOpsSummaryVO> workSummary(@RequestBody(required = false) BoardTaskOpsQueryDTO query) {
        return Result.ok(boardTaskOpsService.summary(query == null ? new BoardTaskOpsQueryDTO() : query));
    }

    @Operation(summary = "任务看板完成率-员工汇总")
    @PostMapping("/work/person-rate")
    @RequiresPermission({"board:view", "board:task", "task:list", "task:mine"})
    public Result<PageResult<BoardTaskOpsPersonRateVO>> workPersonRate(@RequestBody BoardTaskOpsDrillQueryDTO query) {
        return Result.ok(boardTaskOpsService.personRate(query));
    }

    @Operation(summary = "任务看板下钻明细")
    @PostMapping("/work/drill")
    @RequiresPermission({"board:view", "board:task", "task:list", "task:mine"})
    public Result<PageResult<BoardTaskOpsRowVO>> workDrill(@RequestBody BoardTaskOpsDrillQueryDTO query) {
        return Result.ok(boardTaskOpsService.drill(query));
    }

    @Operation(summary = "老板看板图表下钻（主题/人 × GEO/任务）")
    @PostMapping("/chart/drill")
    @RequiresPermission({"board:view", "board:task", "task:list", "geo:weekly:list", "geo:expose:list"})
    public Result<BoardChartDrillVO> chartDrill(@RequestBody(required = false) BoardChartDrillQueryDTO query) {
        return Result.ok(boardChartDrillService.drill(query == null ? new BoardChartDrillQueryDTO() : query));
    }
}
