package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO 话题×平台分组柱状图（横轴=日期；可下钻话题/平台/测试词）")
public class GeoTopicPlatformChartsVO {

    @Schema(description = "层级：topic / platform / keyword", example = "topic")
    private String level;

    @Schema(description = "当前系列维度：topic / platform / keyword", example = "topic")
    private String seriesField;

    @Schema(description = "时间粒度", example = "week")
    private String grain;

    @Schema(description = "当前话题ID", example = "12")
    private Long topicId;

    @Schema(description = "当前话题名称")
    private String topicName;

    @Schema(description = "当前目标问题 / 测试词")
    private String keyword;

    @Schema(description = "下钻当前平台")
    private String platform;

    @Schema(description = "露出平均排名（avg=提及排名之和/提及次数）")
    private List<GeoChartPointVO> rankChart = new ArrayList<>();

    @Schema(description = "测试问题数（跨平台同一问题计 2）")
    private List<GeoChartPointVO> sampleChart = new ArrayList<>();

    @Schema(description = "负面/错误问题数")
    private List<GeoChartPointVO> negativeChart = new ArrayList<>();

    @Schema(description = "首位推荐率%（首位推荐次数÷提及次数；话题层=各平台均值）")
    private List<GeoChartPointVO> firstRecommendChart = new ArrayList<>();

    @Schema(description = "前三位推荐率%（前三推荐次数÷提及次数；话题层=各平台均值）")
    private List<GeoChartPointVO> top3RecommendChart = new ArrayList<>();
}
