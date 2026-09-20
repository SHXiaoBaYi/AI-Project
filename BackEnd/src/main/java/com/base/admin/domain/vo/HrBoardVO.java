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

    @Schema(description = "周期")
    private CycleBlock cycle = new CycleBlock();

    @Schema(description = "HC完成")
    private HcBlock hc = new HcBlock();

    @Schema(description = "面试")
    private InterviewBlock interview = new InterviewBlock();

    @Data
    @Schema(description = "漏斗节点")
    public static class FunnelNode {
        private String stageCode;
        private String stageName;
        private boolean uncollected;
        private long count;
        private Double conversion;
        private Double mom;
        private Double yoy;
    }

    @Data
    @Schema(description = "周期")
    public static class CycleBlock {
        private Double avgDays;
        private Double screenToFirstDays;
        private Double firstToSecondDays;
        private List<NamedDays> byJob = new ArrayList<>();
        private List<ChartPoint> trend = new ArrayList<>();
    }

    @Data
    @Schema(description = "岗位周期")
    public static class NamedDays {
        private String name;
        private Double days;
    }

    @Data
    @Schema(description = "HC")
    public static class HcBlock {
        private long demand;
        private long arrived;
        private long gap;
        private long closed;
        private long paused;
        private Double completionRate;
        private List<DeptBar> byDept = new ArrayList<>();
        private List<HcRow> rows = new ArrayList<>();
        private List<ChartPoint> trend = new ArrayList<>();
    }

    @Data
    @Schema(description = "部门HC")
    public static class DeptBar {
        private String deptName;
        private long demand;
        private long arrived;
        private long gap;
    }

    @Data
    @Schema(description = "岗位进度")
    public static class HcRow {
        private Long id;
        private String jobName;
        private String location;
        private String status;
        private Integer priority;
        private String targetText;
        private LocalDate receivedDate;
        private LocalDate onboardDate;
        private int headcount;
        private int arrived;
        private int gap;
        private boolean warning;
    }

    @Data
    @Schema(description = "面试统计")
    public static class InterviewBlock {
        private List<RoundCount> rounds = new ArrayList<>();
        private List<NamedDays> interviewers = new ArrayList<>();
        private List<ChartPoint> trend = new ArrayList<>();
    }

    @Data
    @Schema(description = "轮次场次")
    public static class RoundCount {
        private int roundNo;
        private String roundName;
        private long count;
    }

    @Data
    @Schema(description = "日期趋势点")
    public static class ChartPoint {
        private String axis;
        private String series;
        private double value;
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
    }
}
