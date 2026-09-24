package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "GEO 话题×关键字×平台 组合汇总行")
public class GeoDailyComboVO {

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "话题名称", example = "送礼")
    private String topicName;

    @Schema(description = "关键字", example = "新疆适合寄内地的礼品")
    private String keyword;

    @Schema(description = "平台", example = "豆包")
    private String platform;

    @Schema(description = "日期范围内监测天数", example = "12")
    private Integer dayCount;

    @Schema(description = "露出天数", example = "8")
    private Integer mentionCount;

    @Schema(description = "有负面/错误的天数", example = "1")
    private Integer negativeCount;

    @Schema(description = "平均排名（仅有排名的天）", example = "3.5")
    private BigDecimal avgRank;

    @Schema(description = "范围内最早巡查日")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate firstDate;

    @Schema(description = "范围内最晚巡查日")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastDate;

    @Schema(description = "最近一天是否露出", example = "1")
    private Integer latestMentioned;

    @Schema(description = "最近一天排名", example = "2", nullable = true)
    private Integer latestRankNo;

    @Schema(description = "最近一天推荐状态", example = "出现且推荐")
    private String latestRecommendStatus;
}
