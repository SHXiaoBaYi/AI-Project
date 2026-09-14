package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO周报看板（由日监测实时聚合）")
public class GeoWeeklyBoardVO {

    @Schema(description = "提及率折线（横轴=周，系列=平台）")
    private List<GeoChartPointVO> mentionChart = new ArrayList<>();

    @Schema(description = "首位提及率折线（横轴=周，系列=平台）")
    private List<GeoChartPointVO> firstMentionChart = new ArrayList<>();

    @Schema(description = "推荐次数柱状（横轴=周，系列=平台）")
    private List<GeoChartPointVO> recommendChart = new ArrayList<>();

    @Schema(description = "周报明细")
    private List<GeoWeeklyRowVO> rows = new ArrayList<>();

    @Data
    @Schema(description = "周报一行")
    public static class GeoWeeklyRowVO {
        @Schema(description = "周标签", example = "2026年第36周")
        private String weekLabel;

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
    }
}
