package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.BoardChartDrillQueryDTO;
import com.base.admin.domain.dto.BoardTaskDrillQueryDTO;
import com.base.admin.domain.dto.BoardTaskSummaryQueryDTO;
import com.base.admin.domain.vo.BoardChartDrillVO;
import com.base.admin.domain.vo.BoardTaskSummaryVO;
import com.base.admin.domain.vo.SysTaskVO;
import com.base.admin.service.BoardChartDrillService;
import com.base.admin.service.BoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "统一数据看板", description = "露出/投放/任务聚合看板")
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final BoardChartDrillService boardChartDrillService;

    @Operation(summary = "任务看板汇总")
    @PostMapping("/task/summary")
    @RequiresPermission({"board:view", "board:task", "task:list", "task:mine"})
    public Result<BoardTaskSummaryVO> taskSummary(@RequestBody(required = false) BoardTaskSummaryQueryDTO query) {
        return Result.ok(boardService.taskSummary(query == null ? new BoardTaskSummaryQueryDTO() : query));
    }

    @Operation(summary = "任务看板下钻")
    @PostMapping("/task/drill")
    @RequiresPermission({"board:view", "board:task", "task:list", "task:mine"})
    public Result<PageResult<SysTaskVO>> taskDrill(@RequestBody BoardTaskDrillQueryDTO query) {
        return Result.ok(boardService.taskDrill(query));
    }

    @Operation(summary = "老板看板图表下钻（主题/人 × GEO/任务）")
    @PostMapping("/chart/drill")
    @RequiresPermission({"board:view", "board:task", "task:list", "geo:weekly:list", "geo:expose:list"})
    public Result<BoardChartDrillVO> chartDrill(@RequestBody(required = false) BoardChartDrillQueryDTO query) {
        return Result.ok(boardChartDrillService.drill(query == null ? new BoardChartDrillQueryDTO() : query));
    }
}
