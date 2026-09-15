package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO日报看板（由日监测实时聚合）")
public class GeoDailyBoardVO {

    @Schema(description = "提及率折线（横轴=日期，系列=平台）")
    private List<GeoChartPointVO> mentionChart = new ArrayList<>();

    @Schema(description = "首位提及率折线（横轴=日期，系列=平台）")
    private List<GeoChartPointVO> firstMentionChart = new ArrayList<>();

    @Schema(description = "推荐次数柱状（横轴=日期，系列=平台）")
    private List<GeoChartPointVO> recommendChart = new ArrayList<>();

    @Schema(description = "日报明细（扁平）")
    private List<GeoDailyRowVO> rows = new ArrayList<>();

    @Schema(description = "汇总展示（按日期 Tab：话题/关键字/负责人 + 平台明细，对齐日监测新增）")
    private List<GeoDailySummaryDateVO> summaryGroups = new ArrayList<>();

    @Schema(description = "负责人维度-提及率折线")
    private List<GeoChartPointVO> ownerMentionChart = new ArrayList<>();

    @Schema(description = "负责人维度-首位提及率折线")
    private List<GeoChartPointVO> ownerFirstMentionChart = new ArrayList<>();

    @Schema(description = "负责人维度-推荐次数柱状")
    private List<GeoChartPointVO> ownerRecommendChart = new ArrayList<>();

    @Schema(description = "负责人维度-明细")
    private List<GeoDailyRowVO> ownerRows = new ArrayList<>();

    @Schema(description = "负责人维度汇总（按日期 Tab：负责人 + 平台明细）")
    private List<GeoDailySummaryDateVO> ownerSummaryGroups = new ArrayList<>();

    @Data
    @Schema(description = "日报一行")
    public static class GeoDailyRowVO {
        @Schema(description = "日期", example = "2026-09-01")
        private String dateLabel;

        @Schema(description = "话题（话题维度）")
        private String topicName;

        @Schema(description = "关键字")
        private String keyword;

        @Schema(description = "负责人（负责人维度）")
        private String ownerName;

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

    @Data
    @Schema(description = "日报汇总-按日期")
    public static class GeoDailySummaryDateVO {
        @Schema(description = "巡查日期", example = "2026-09-15")
        private String inspectDate;

        @Schema(description = "话题行（关键字绑定负责人）")
        private List<GeoDailySummaryTopicVO> topics = new ArrayList<>();
    }

    @Data
    @Schema(description = "日报汇总-话题行")
    public static class GeoDailySummaryTopicVO {
        @Schema(description = "话题ID", example = "1")
        private Long topicId;

        @Schema(description = "话题名称")
        private String topicName;

        @Schema(description = "关键字")
        private String keyword;

        @Schema(description = "负责人")
        private String ownerName;

        @Schema(description = "平台明细（对齐新增抽屉）")
        private List<GeoDailySummaryPlatformVO> platforms = new ArrayList<>();
    }

    @Data
    @Schema(description = "日报汇总-平台指标")
    public static class GeoDailySummaryPlatformVO {
        @Schema(description = "记录ID", example = "1")
        private Long id;

        @Schema(description = "平台")
        private String platform;

        @Schema(description = "话题名称（负责人维度明细用）")
        private String topicName;

        @Schema(description = "关键字（负责人维度明细用）")
        private String keyword;

        @Schema(description = "是否提及（1=是 0=否）", example = "1")
        private Integer mentioned;

        @Schema(description = "排名", example = "1")
        private Integer rankNo;

        @Schema(description = "推荐状态")
        private String recommendStatus;

        @Schema(description = "第三方链接")
        private String thirdPartyUrl;

        @Schema(description = "竞品")
        private String competitors;

        @Schema(description = "负面内容")
        private String negativeContent;

        @Schema(description = "截图URL")
        private String screenshotUrl;
    }
}
