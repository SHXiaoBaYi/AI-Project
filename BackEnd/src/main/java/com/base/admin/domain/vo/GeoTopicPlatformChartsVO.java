package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO 话题×平台分组柱状图（横轴=日期，系列=平台；可下钻话题/目标问题）")
public class GeoTopicPlatformChartsVO {

    @Schema(description = "层级：topic / question / platform", example = "topic")
    private String level;

    @Schema(description = "当前系列维度：topic / question / platform", example = "topic")
    private String seriesField;

    @Schema(description = "时间粒度", example = "week")
    private String grain;

    @Schema(description = "当前话题ID", example = "12")
    private Long topicId;

    @Schema(description = "当前话题名称")
    private String topicName;

    @Schema(description = "当前目标问题")
    private String keyword;

    @Schema(description = "露出平均排名（横轴=日期；系列随 level 变化）")
    private List<GeoChartPointVO> rankChart = new ArrayList<>();

    @Schema(description = "测试问题数量（横轴=日期；系列随 level 变化）")
    private List<GeoChartPointVO> sampleChart = new ArrayList<>();

    @Schema(description = "负面/错误内容数量（横轴=日期；系列随 level 变化）")
    private List<GeoChartPointVO> negativeChart = new ArrayList<>();
}
