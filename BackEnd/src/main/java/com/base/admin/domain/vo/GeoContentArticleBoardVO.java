package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "文章发布、收录情况看板")
public class GeoContentArticleBoardVO {

    @Schema(description = "发布数量折线/柱状（横轴=日期，系列=发布平台或员工）")
    private List<GeoChartPointVO> publishCountChart = new ArrayList<>();

    @Schema(description = "AI收录率折线（横轴=日期，系列=AI平台或员工）")
    private List<GeoChartPointVO> citeRateChart = new ArrayList<>();

    @Schema(description = "员工AI收录对比（柱状）")
    private List<GeoChartPointVO> publisherCiteCompareChart = new ArrayList<>();

    @Schema(description = "发布平台被引用次数排行")
    private List<GeoRankItemVO> publishPlatformCiteRank = new ArrayList<>();

    @Schema(description = "文章被引用次数排行")
    private List<GeoRankItemVO> articleCiteRank = new ArrayList<>();

    @Schema(description = "发布数量明细行（可点开详情）")
    private List<GeoContentPublishAggRowVO> publishRows = new ArrayList<>();

    @Schema(description = "收录率明细行")
    private List<GeoContentCiteAggRowVO> citeRows = new ArrayList<>();

    @Schema(description = "员工收录对比行（含同比环比）")
    private List<GeoContentPublisherCiteRowVO> publisherCiteRows = new ArrayList<>();

    @Schema(description = "员工收录对比说明")
    private String publisherCompareHint;
}
