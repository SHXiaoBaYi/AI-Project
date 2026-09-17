package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "看板图表点")
public class BoardChartPointVO {

    @Schema(description = "横轴/名称")
    private String axis;

    @Schema(description = "数值")
    private double value;

    @Schema(description = "系列")
    private String series;
}
