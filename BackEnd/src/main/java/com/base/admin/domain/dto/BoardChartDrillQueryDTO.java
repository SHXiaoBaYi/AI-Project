package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "老板看板图表下钻查询")
public class BoardChartDrillQueryDTO {

    @Schema(description = "业务域 geo|task", example = "geo")
    private String domain;

    @Schema(description = "聚合维度 topic|person", example = "topic")
    private String dim;

    @Schema(description = "GEO人角色 writer|publisher|owner，默认 writer", example = "writer")
    private String personRole;

    @Schema(description = "时间粒度 day|week|month|year", example = "week")
    private String grain;

    @Schema(description = "开始日期 yyyy-MM-dd", example = "2026-09-08")
    private String startDate;

    @Schema(description = "结束日期 yyyy-MM-dd", example = "2026-09-14")
    private String endDate;

    @Schema(description = "指标：geo 默认 mentionRate；task 默认 doneRate", example = "mentionRate")
    private String metric;

    @Schema(description = "已下钻路径")
    private List<BoardChartStackItemDTO> stack = new ArrayList<>();

    @Schema(description = "本次点击的柱 key；空表示取当前栈顶图")
    private String clickKey;
}
