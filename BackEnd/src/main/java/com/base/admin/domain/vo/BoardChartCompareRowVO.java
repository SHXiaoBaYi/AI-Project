package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "看板同比环比对比行（分平台）")
public class BoardChartCompareRowVO {

    @Schema(description = "话题")
    private String topic;

    @Schema(description = "AI平台")
    private String platform;

    @Schema(description = "本期值")
    private double value;

    @Schema(description = "环比变化（百分点）")
    private Double mom;

    @Schema(description = "同比变化（百分点）")
    private Double yoy;

    @Schema(description = "样本数")
    private long sampleCount;
}
