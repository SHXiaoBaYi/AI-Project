package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO月报看板")
public class GeoMonthlyBoardVO {

    @Schema(description = "提及率折线（横轴=月，系列=平台）")
    private List<GeoChartPointVO> mentionChart = new ArrayList<>();

    @Schema(description = "首位提及率折线（横轴=月，系列=平台）")
    private List<GeoChartPointVO> firstMentionChart = new ArrayList<>();

    @Schema(description = "推荐次数柱状（横轴=月，系列=平台）")
    private List<GeoChartPointVO> recommendChart = new ArrayList<>();

    @Schema(description = "月报明细")
    private List<GeoMonthlyRowVO> rows = new ArrayList<>();

    @Schema(description = "查询范围内已落库的周期数", example = "2")
    private int persistedPeriodCount;

    @Data
    @Schema(description = "月报一行")
    public static class GeoMonthlyRowVO {
        @Schema(description = "月标签", example = "2026年09月")
        private String monthLabel;

        @Schema(description = "话题")
        private String topicName;

        @Schema(description = "平台")
        private String platform;

        @Schema(description = "样本数", example = "12")
        private int sampleCount;

        @Schema(description = "提及率%", example = "37.50")
        private double mentionRate;

        @Schema(description = "首位提及率%", example = "70.00")
        private double firstMentionRate;

        @Schema(description = "推荐次数", example = "5")
        private int recommendCount;

        @Schema(description = "竞品TOP")
        private String competitorTop;

        @Schema(description = "引用平台TOP")
        private String citePlatformTop;

        @Schema(description = "是否已落库", example = "true")
        private boolean fromSnapshot;
    }
}
