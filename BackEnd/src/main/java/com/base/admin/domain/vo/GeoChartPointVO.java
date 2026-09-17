package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "看板图表点")
public class GeoChartPointVO {

    @Schema(description = "横轴", example = "2026年第36周")
    private String axis;

    @Schema(description = "系列（平台或指标名）", example = "豆包-露出率")
    private String series;

    @Schema(description = "数值", example = "37.5")
    private double value;

    @Schema(description = "下钻键（话题ID或目标问题原文）", example = "12")
    private String key;
}
