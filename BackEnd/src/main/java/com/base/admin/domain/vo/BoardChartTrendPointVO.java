package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日期范围折线点（同日可多系列/主题）")
public class BoardChartTrendPointVO {

    @Schema(description = "时间轴标签", example = "2026-09-10")
    private String axis;

    @Schema(description = "系列展示名（主题/人等）", example = "新疆羊肉")
    private String series;

    @Schema(description = "系列下钻 key（与柱图 bars.key 对齐）")
    private String seriesKey;

    @Schema(description = "数值")
    private double value;

    @Schema(description = "样本数")
    private long sampleCount;

    @Schema(description = "该系列是否可下钻")
    private boolean drillable;
}
