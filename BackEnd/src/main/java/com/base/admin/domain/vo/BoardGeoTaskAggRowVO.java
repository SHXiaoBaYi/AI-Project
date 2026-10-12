package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO任务看板维度汇总行")
public class BoardGeoTaskAggRowVO {

    @Schema(description = "维度键（话题ID/撰写人ID/任务类型名；未分配为0）", example = "1")
    private String dimKey;

    @Schema(description = "展示名", example = "新疆羊肉")
    private String name;

    @Schema(description = "话题ID（dim=topic）", example = "1", nullable = true)
    private Long topicId;

    @Schema(description = "撰写人用户ID（dim=writer）", example = "2", nullable = true)
    private Long writerUserId;

    @Schema(description = "任务类型（dim=taskType）", example = "文章撰写")
    private String taskType;

    @Schema(description = "已完成数（完成率分子）", example = "8")
    private long doneCount;

    @Schema(description = "纳入完成率的任务总数（分母，口径同任务看板）", example = "12")
    private long totalCount;

    @Schema(description = "完成率%", example = "66.67")
    private double completionRate;

    @Schema(description = "未完成数（待分配/未开始/待处理/进行中等）", example = "4")
    private long openCount;

    @Schema(description = "区间内应到期（未完成且计划截止在区间）", example = "5")
    private long dueCount;

    @Schema(description = "区间内逾期（应到期且已超时）", example = "1")
    private long overdueCount;
}
