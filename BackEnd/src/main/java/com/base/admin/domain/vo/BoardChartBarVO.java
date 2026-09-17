package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "图表下钻柱")
public class BoardChartBarVO {

    @Schema(description = "柱 key（下钻用）")
    private String key;

    @Schema(description = "横轴展示")
    private String label;

    @Schema(description = "本期值")
    private double value;

    @Schema(description = "环比变化（百分点或数量差）")
    private Double mom;

    @Schema(description = "同比变化")
    private Double yoy;

    @Schema(description = "样本数/任务数")
    private long sampleCount;

    @Schema(description = "是否可继续下钻")
    private boolean drillable;
}
