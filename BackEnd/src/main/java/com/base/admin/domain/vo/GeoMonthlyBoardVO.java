package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO月报看板")
public class GeoMonthlyBoardVO {

    @Schema(description = "露出率折线（横轴=月，系列=平台）")
    private List<GeoChartPointVO> mentionChart = new ArrayList<>();

    @Schema(description = "首位露出率折线（横轴=月，系列=平台）")
    private List<GeoChartPointVO> firstMentionChart = new ArrayList<>();

    @Schema(description = "推荐次数柱状（横轴=月，系列=平台）")
    private List<GeoChartPointVO> recommendChart = new ArrayList<>();

    @Schema(description = "月报明细")
    private List<GeoMonthlyRowVO> rows = new ArrayList<>();

    @Schema(description = "查询范围内已落库的周期数", example = "2")
    private int persistedPeriodCount;

    @Schema(description = "同比/环比汇总（按查询范围内最新周期，话题维度）")
    private GeoBoardCompareSummaryVO compareSummary;

    @Schema(description = "负责人维度-露出率折线")
    private List<GeoChartPointVO> ownerMentionChart = new ArrayList<>();

    @Schema(description = "负责人维度-首位露出率折线")
    private List<GeoChartPointVO> ownerFirstMentionChart = new ArrayList<>();

    @Schema(description = "负责人维度-推荐次数柱状")
    private List<GeoChartPointVO> ownerRecommendChart = new ArrayList<>();

    @Schema(description = "负责人维度-明细")
    private List<GeoMonthlyRowVO> ownerRows = new ArrayList<>();

    @Schema(description = "负责人维度-同比/环比汇总")
    private GeoBoardCompareSummaryVO ownerCompareSummary;

    @Data
    @Schema(description = "月报一行")
    public static class GeoMonthlyRowVO {
        @Schema(description = "月标签", example = "2026年09月")
        private String monthLabel;

        @Schema(description = "话题（话题维度）")
        private String topicName;

        @Schema(description = "负责人（负责人维度）")
        private String ownerName;

        @Schema(description = "平台")
        private String platform;

        @Schema(description = "样本数", example = "12")
        private int sampleCount;

        @Schema(description = "露出率%", example = "37.50")
        private double mentionRate;

        @Schema(description = "露出率环比（百分点）", nullable = true)
        private Double mentionRateMom;

        @Schema(description = "露出率同比（百分点）", nullable = true)
        private Double mentionRateYoy;

        @Schema(description = "首位露出率%", example = "70.00")
        private double firstMentionRate;

        @Schema(description = "首位露出率环比（百分点）", nullable = true)
        private Double firstMentionRateMom;

        @Schema(description = "首位露出率同比（百分点）", nullable = true)
        private Double firstMentionRateYoy;

        @Schema(description = "推荐次数", example = "5")
        private int recommendCount;

        @Schema(description = "推荐次数环比", nullable = true)
        private Integer recommendCountMom;

        @Schema(description = "推荐次数同比", nullable = true)
        private Integer recommendCountYoy;

        @Schema(description = "竞品TOP")
        private String competitorTop;

        @Schema(description = "引用平台TOP")
        private String citePlatformTop;

        @Schema(description = "是否已落库", example = "true")
        private boolean fromSnapshot;
    }
}
