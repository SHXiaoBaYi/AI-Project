package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO全年目标看板（由日监测实时聚合）")
public class GeoYearlyBoardVO {

    @Schema(description = "实际达成折线（横轴=时段/话题，系列=平台）")
    private List<GeoChartPointVO> actualChart = new ArrayList<>();

    @Schema(description = "达成率柱状（横轴=时段/话题，系列=平台）")
    private List<GeoChartPointVO> achieveChart = new ArrayList<>();

    @Schema(description = "达成明细")
    private List<GeoYearlyRowVO> rows = new ArrayList<>();

    @Data
    @Schema(description = "全年达成一行")
    public static class GeoYearlyRowVO {
        @Schema(description = "时间段", example = "2026")
        private String periodLabel;

        @Schema(description = "话题")
        private String topicName;

        @Schema(description = "平台")
        private String platform;

        @Schema(description = "目标%", example = "80.00")
        private BigDecimal targetRate;

        @Schema(description = "实际达成%", example = "86.00")
        private double actualRate;

        @Schema(description = "达成率%（实际/目标）", example = "107.50")
        private double achieveRate;

        @Schema(description = "样本数", example = "40")
        private int sampleCount;
    }
}
