package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO全年目标看板（落库优先，未落库则实时聚合）")
public class GeoYearlyBoardVO {

    @Schema(description = "实际达成折线（横轴=时段/话题，系列=平台）")
    private List<GeoChartPointVO> actualChart = new ArrayList<>();

    @Schema(description = "达成率柱状（横轴=时段/话题，系列=平台）")
    private List<GeoChartPointVO> achieveChart = new ArrayList<>();

    @Schema(description = "达成明细")
    private List<GeoYearlyRowVO> rows = new ArrayList<>();

    @Schema(description = "查询范围内已落库的周期数", example = "2")
    private int persistedPeriodCount;

    @Schema(description = "同比/环比汇总（按查询范围内最新周期，话题维度）")
    private GeoBoardCompareSummaryVO compareSummary;

    @Schema(description = "负责人维度-实际达成折线")
    private List<GeoChartPointVO> ownerActualChart = new ArrayList<>();

    @Schema(description = "负责人维度-达成率柱状")
    private List<GeoChartPointVO> ownerAchieveChart = new ArrayList<>();

    @Schema(description = "负责人维度-明细")
    private List<GeoYearlyRowVO> ownerRows = new ArrayList<>();

    @Schema(description = "负责人维度-同比/环比汇总")
    private GeoBoardCompareSummaryVO ownerCompareSummary;

    @Data
    @Schema(description = "全年达成一行")
    public static class GeoYearlyRowVO {
        @Schema(description = "时间段", example = "2026")
        private String periodLabel;

        @Schema(description = "话题（话题维度）")
        private String topicName;

        @Schema(description = "负责人（负责人维度）")
        private String ownerName;

        @Schema(description = "平台")
        private String platform;

        @Schema(description = "目标%", example = "80.00")
        private BigDecimal targetRate;

        @Schema(description = "实际达成%", example = "86.00")
        private double actualRate;

        @Schema(description = "实际达成环比（百分点）", nullable = true)
        private Double actualRateMom;

        @Schema(description = "实际达成同比（百分点）", nullable = true)
        private Double actualRateYoy;

        @Schema(description = "达成率%（实际/目标）", example = "107.50")
        private double achieveRate;

        @Schema(description = "达成率环比（百分点）", nullable = true)
        private Double achieveRateMom;

        @Schema(description = "达成率同比（百分点）", nullable = true)
        private Double achieveRateYoy;

        @Schema(description = "样本数", example = "40")
        private int sampleCount;

        @Schema(description = "是否已落库", example = "true")
        private boolean fromSnapshot;
    }
}
