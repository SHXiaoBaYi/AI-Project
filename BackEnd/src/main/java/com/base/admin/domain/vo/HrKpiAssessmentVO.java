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

    @Schema(description = "P0 完成记分（每 HC）", example = "2.0")
    private Double weightP0;

    @Schema(description = "P1 完成记分（每达标一次）", example = "1.0")
    private Double weightP1;

    @Schema(description = "P0：完成多少人入职记一次分", example = "1")
    private Integer p0OnboardCount;

    @Schema(description = "P1：周期内合格简历份数门槛（与入职二选一）", example = "3")
    private Integer p1QualifiedResumeCount;

    @Schema(description = "P1：周期内入职人数门槛（与合格简历二选一）", example = "1")
    private Integer p1OnboardCount;

    @Schema(description = "口径说明")
    private String scoreRuleHint;

    @Schema(description = "整体完成率 0~1（当前筛选范围内全部到期任务）")
    private Double overallCompletionRate;

    @Schema(description = "整体已完成权重")
    private Double overallDoneWeight;

    @Schema(description = "整体应计总权重")
    private Double overallTotalWeight;

    @Schema(description = "整体已得分（各负责人已得分之和，整数）", example = "11")
    private Double overallScore;

    @Schema(description = "整体满分=负责人数×基准分，展示为 overallScore/overallScoreMax", example = "180")
    private Double overallScoreMax;

    @Schema(description = "参与核算的招聘负责人数", example = "6")
    private Integer ownerCount;

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
        @Schema(description = "P1 因入职达标的次数（每次对应规则中的入职门槛）")
        private Integer p1DoneOnboard;
        @Schema(description = "P1 因合格简历满门槛达标的次数")
        private Integer p1DoneResume;
        @Schema(description = "P1 未满门槛的合格简历进度份数（计入折算分）", example = "2")
        private Integer p1ResumePartialCount;
        @Schema(description = "P1 未满门槛简历折算分，如 2/3→0.66", example = "0.66")
        private Double p1ResumePartialScore;
        @Schema(description = "应计总权重")
        private Double totalWeight;
        @Schema(description = "已得权重（含 P1 简历进度折算）")
        private Double doneWeight;
        @Schema(description = "整体完成率 0~1")
        private Double completionRate;
        @Schema(description = "已得分（可含小数；前端展示为 已得分/基准分）")
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
