package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "GEO任务看板下钻任务行")
public class BoardGeoTaskRowVO {

    @Schema(description = "任务ID", example = "100")
    private Long id;

    @Schema(description = "标题", example = "撰写：新疆羊肉怎么选")
    private String title;

    @Schema(description = "任务类型", example = "文章撰写")
    private String taskType;

    @Schema(description = "状态", example = "进行中")
    private String status;

    @Schema(description = "优先级 1低2中3高4紧急", example = "2")
    private Integer priority;

    @Schema(description = "进度0-100", example = "40")
    private Integer progress;

    @Schema(description = "话题名称", example = "新疆羊肉")
    private String topicName;

    @Schema(description = "撰写人", example = "甄德明")
    private String writerName;

    @Schema(description = "负责人", example = "张三")
    private String ownerName;

    @Schema(description = "执行人（逗号拼接）", example = "李四")
    private String assigneeNames;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "计划截止")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planEndTime;

    @Schema(description = "实际完成")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    @Schema(description = "是否逾期（未完成）", example = "false")
    private boolean overdue;

    @Schema(description = "已超时天数", example = "2", nullable = true)
    private Integer overdueDays;

    @Schema(description = "时效标签：ON_TIME/EARLY/LATE", example = "ON_TIME")
    private String timingTag;

    @Schema(description = "时效文案", example = "正常完成")
    private String timingLabel;
}
