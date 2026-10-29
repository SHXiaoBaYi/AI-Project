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
        private Integer score;
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
        private Integer score;
    }
}
