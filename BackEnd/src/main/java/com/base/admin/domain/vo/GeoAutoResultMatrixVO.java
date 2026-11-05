package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "GEO自动监测-问题×平台矩阵视图")
public class GeoAutoResultMatrixVO {

    @Schema(description = "矩阵列：平台名（按 geo_platform 排序）", example = "[\"豆包\",\"DS\"]")
    private List<String> platforms;

    @Schema(description = "矩阵行：每行一个问题")
    private List<Row> rows;

    @Data
    @Schema(description = "矩阵行")
    public static class Row {

        @Schema(description = "问题ID", example = "1")
        private Long questionId;

        @Schema(description = "问题文本", example = "新疆适合寄内地的礼品有哪些？")
        private String questionText;

        @Schema(description = "话题ID", example = "1")
        private Long topicId;

        @Schema(description = "话题名称", example = "送礼")
        private String topicName;

        @Schema(description = "提及平台数 / 参与平台数", example = "3/4")
        private String mentionSummary;

        @Schema(description = "平台 -> 最新监测格子；无数据的平台不在 map 中")
        private Map<String, Cell> cells;
    }

    @Data
    @Schema(description = "矩阵格子：该问题在该平台的最新一次监测")
    public static class Cell {

        @Schema(description = "结果ID", example = "1")
        private Long resultId;

        @Schema(description = "监测日期", example = "2026-11-05")
        private String runDate;

        @Schema(description = "是否提及 1=是 0=否", example = "1")
        private Integer mentioned;

        @Schema(description = "排名", example = "2")
        private Integer rankNo;

        @Schema(description = "推荐状态", example = "出现且推荐")
        private String recommendStatus;

        @Schema(description = "情感", example = "正面")
        private String sentiment;

        @Schema(description = "状态：SUCCESS/SKIPPED_MANUAL/JUDGE_FAILED/CALL_FAILED", example = "SUCCESS")
        private String status;
    }
}
