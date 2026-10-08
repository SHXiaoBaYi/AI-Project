package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "招聘 KPI 统计看板")
public class HrKpiBoardVO {

    @Schema(description = "汇总指标")
    private Summary summary = new Summary();

    @Schema(description = "招聘人员绩效行")
    private List<OwnerRow> owners = new ArrayList<>();

    @Schema(description = "逾期岗位明细")
    private List<JobRow> overdueJobs = new ArrayList<>();

    @Schema(description = "区间内成功入职明细")
    private List<OnboardRow> onboardings = new ArrayList<>();

    @Schema(description = "入职趋势（柱/折线）")
    private List<ChartPoint> onboardTrend = new ArrayList<>();

    @Schema(description = "转化漏斗筛选→入职")
    private List<ChartPoint> conversionFunnel = new ArrayList<>();

    @Schema(description = "各阶段平均用时（天）")
    private List<ChartPoint> stageCycle = new ArrayList<>();

    @Schema(description = "负责人雷达图指标")
    private List<RadarPoint> ownerRadar = new ArrayList<>();

    @Schema(description = "岗位分级分布")
    private List<ChartPoint> gradeDistribution = new ArrayList<>();

    @Data
    @Schema(description = "汇总")
    public static class Summary {
        @Schema(description = "当前在招岗位总数")
        private Integer openJobs;

        @Schema(description = "区间成功入职人数")
        private Integer onboardedCount;

        @Schema(description = "岗位按期完成率 0~1")
        private Double onTimeRate;

        @Schema(description = "逾期岗位数量")
        private Integer overdueCount;

        @Schema(description = "超期完成岗位数（完成后晚于目标）")
        private Integer overdueCompletedCount;

        @Schema(description = "超额完成岗位数（入职人数>需求人数）")
        private Integer overQuotaCount;

        @Schema(description = "筛选→入职转化率 0~1")
        private Double conversionRate;

        @Schema(description = "平均招聘周期（天）")
        private Double avgCycleDays;
    }

    @Data
    @Schema(description = "负责人绩效")
    public static class OwnerRow {
        private Long ownerUserId;
        private String ownerName;
        private Integer openJobs;
        private Integer onboardedCount;
        private Double onTimeRate;
        private Integer overdueCount;
        private Integer overdueCompletedCount;
        private Integer overQuotaCount;
        private Double conversionRate;
        private Double avgCycleDays;
    }

    @Data
    @Schema(description = "岗位行")
    public static class JobRow {
        private Long id;
        private String jobName;
        private String deptName;
        private String ownerNames;
        private String status;
        private Integer priority;
        private String priorityLabel;
        private Integer importanceLevel;
        private String importanceLabel;
        private Integer urgencyLevel;
        private String urgencyLabel;
        private Integer difficultyLevel;
        private String difficultyLabel;
        private Integer headcount;
        private Integer arrived;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate receivedDate;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate targetDate;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate onboardDate;
        private Integer overdueDays;
        private String progressStatus;
    }

    @Data
    @Schema(description = "入职明细")
    public static class OnboardRow {
        private Long applicationId;
        private String candidateName;
        private String jobName;
        private String ownerNames;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate onboardDate;
        private Integer cycleDays;
    }

    @Data
    @Schema(description = "图表点")
    public static class ChartPoint {
        private String axis;
        private String series;
        private Double value;
    }

    @Data
    @Schema(description = "雷达点")
    public static class RadarPoint {
        private String ownerName;
        private String metric;
        private Double value;
    }
}
