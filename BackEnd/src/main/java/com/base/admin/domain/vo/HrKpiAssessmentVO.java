package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "考核得分面板")
public class HrKpiAssessmentVO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "核算月（取月初日）")
    private LocalDate settleMonth;

    @Schema(description = "基准分（完成率×基准分=实得分）", example = "30")
    private Double baseScore;

    @Schema(description = "P0 岗位权重", example = "2.0")
    private Double weightP0;

    @Schema(description = "P1 岗位权重", example = "1.0")
    private Double weightP1;

    @Schema(description = "口径说明")
    private String scoreRuleHint;

    @Schema(description = "整体完成率 0~1（当前筛选范围内全部到期任务）")
    private Double overallCompletionRate;

    @Schema(description = "整体已完成权重")
    private Double overallDoneWeight;

    @Schema(description = "整体应计总权重")
    private Double overallTotalWeight;

    @Schema(description = "整体实得分（完成率×基准分）")
    private Double overallScore;

    @Schema(description = "按人汇总")
    private List<OwnerSummary> owners = new ArrayList<>();

    @Schema(description = "任务明细")
    private List<TaskRow> tasks = new ArrayList<>();

    @Data
    @Schema(description = "负责人汇总")
    public static class OwnerSummary {
        private Long ownerUserId;
        private String ownerName;
        private Integer dueCount;
        private Integer doneCount;
        private Integer openCount;
        private Integer p0Due;
        private Integer p0Done;
        private Integer p1Due;
        private Integer p1Done;
        @Schema(description = "应计总权重")
        private Double totalWeight;
        @Schema(description = "已完成权重")
        private Double doneWeight;
        @Schema(description = "整体完成率 0~1")
        private Double completionRate;
        @Schema(description = "30 分基准下实得分（完成率×30，无额外加减分）")
        private Double score;
    }

    @Data
    @Schema(description = "考核任务行")
    public static class TaskRow {
        private Long id;
        private Long requisitionId;
        private String jobName;
        private Long ownerUserId;
        private String ownerName;
        private Integer slotNo;
        private String priorityCode;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime publishedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dueAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime originDueAt;
        private String status;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime doneAt;
        private String doneReason;
        @Schema(description = "岗位权重：P0=2 / P1=1")
        private Double weight;
        @Schema(description = "计入完成率的权重（完成=weight，未完成=0）")
        private Double earnedWeight;
        @Deprecated
        @Schema(description = "历史单任务记分字段，展示以 weight/earnedWeight 为准")
        private Integer score;
    }
}
