package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "招聘看板")
public class HrBoardVO {

    @Schema(description = "漏斗")
    private List<FunnelNode> funnel = new ArrayList<>();

    @Schema(description = "招聘周期当前下钻岗位，空表示各岗位")
    private String cycleJob;

    @Schema(description = "招聘周期：各岗位平均周期，或下钻后的各阶段周期")
    private List<ChartPoint> jobCycle = new ArrayList<>();

    @Schema(description = "阶段招聘周期")
    private List<ChartPoint> stageCycle = new ArrayList<>();

    @Schema(description = "招聘完成情况")
    private HcBlock hc = new HcBlock();

    @Schema(description = "面试情况统计")
    private InterviewStats interviewStats = new InterviewStats();

    @Schema(description = "面试官通过率")
    private List<ChartPoint> interviewerPass = new ArrayList<>();

    @Schema(description = "面试淘汰原因")
    private List<PieSlice> failReasons = new ArrayList<>();

    @Schema(description = "各需求面试量趋势")
    private List<ChartPoint> interviewVolume = new ArrayList<>();

    @Data
    @Schema(description = "饼图切片")
    public static class PieSlice {
        private String name;
        private long value;
        @Schema(description = "下钻键，通常等于原因名")
        private String key;
    }

    @Data
    @Schema(description = "漏斗节点")
    public static class FunnelNode {
        private String stageCode;
        private String stageName;
        private boolean uncollected;
        private long count;
        private Double conversion;
        @Schema(description = "转化率名称，例如到面率")
        private String conversionLabel;
        private Double mom;
        private Double yoy;
    }

    @Data
    @Schema(description = "招聘完成情况")
    public static class HcBlock {
        private long demand;
        private long arrived;
        private long gap;
        private long closed;
        private long frozen;
        private Double completionRate;
        private List<ProgressRow> rows = new ArrayList<>();
    }

    @Data
    @Schema(description = "岗位进度")
    public static class ProgressRow {
        private Long id;
        private String jobName;
        private int headcount;
        private String targetText;
        private LocalDate targetDate;
        private int arrived;
        private int gap;
        private String progressStatus;
        private Integer priority;
        private String priorityLabel;
        private String status;
        private LocalDate receivedDate;
        private LocalDate onboardDate;
        private boolean warning;
        private String ownerName;
        private String location;
    }

    @Data
    @Schema(description = "面试情况")
    public static class InterviewStats {
        private long pendingInvite;
        private long invited;
        private long showUp;
        private long round1;
        private long retest;
        private long finalRound;
        private Double round1PassRate;
        private Double retestPassRate;
        private Double finalPassRate;
        private long noShow;
        private Double noShowRate;
    }

    @Data
    @Schema(description = "日期趋势点")
    public static class ChartPoint {
        private String axis;
        private String series;
        @Schema(description = "下钻键，岗位名或面试官用户ID")
        private String seriesKey;
        private double value;
        @Schema(description = "能否继续下钻")
        private boolean drillable;
    }

    @Data
    @Schema(description = "下钻行")
    public static class DrillRow {
        private Long applicationId;
        private Long requisitionId;
        private String candidateName;
        private String jobName;
        private String channel;
        private String stageName;
        private String submitter;
        private LocalDate submittedAt;
        private String interviewer;
        private LocalDateTime interviewAt;
        private String status;
        private String ownerName;
        private String priorityLabel;
        private String funnelStage;
        private LocalDate reachedAt;
    }

    @Data
    @Schema(description = "面试记录下钻")
    public static class InterviewRecordRow {
        private Long recordId;
        private Long applicationId;
        private String candidateName;
        private String jobName;
        private Integer roundNo;
        private String roundName;
        private String conclusion;
        private String failReason;
        private String comment;
        private LocalDateTime interviewedAt;
        private Long interviewerUserId;
        private String interviewerName;
    }
}
