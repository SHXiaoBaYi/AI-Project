package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "老板看板当前层图表")
public class BoardChartDrillVO {

    @Schema(description = "图标题")
    private String title;

    @Schema(description = "业务域")
    private String domain;

    @Schema(description = "当前聚合字段")
    private String axisField;

    @Schema(description = "指标编码")
    private String metric;

    @Schema(description = "指标展示名")
    private String metricLabel;

    @Schema(description = "时间粒度")
    private String grain;

    @Schema(description = "开始日期")
    private String startDate;

    @Schema(description = "结束日期")
    private String endDate;

    @Schema(description = "面包屑")
    private List<BoardChartStackItemVO> breadcrumb = new ArrayList<>();

    @Schema(description = "KPI：本期")
    private Double currentValue;

    @Schema(description = "KPI：环比变化")
    private Double mom;

    @Schema(description = "KPI：同比变化")
    private Double yoy;

    @Schema(description = "柱数据")
    private List<BoardChartBarVO> bars = new ArrayList<>();

    @Schema(description = "日期范围折线（横轴=时间粒度，系列=主题/人等）")
    private List<BoardChartTrendPointVO> trend = new ArrayList<>();

    @Schema(description = "本层是否仍可下钻（若 false 则柱均不可点）")
    private boolean chartDrillable;
}
