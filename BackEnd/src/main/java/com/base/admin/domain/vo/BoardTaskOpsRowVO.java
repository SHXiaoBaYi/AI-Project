package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "任务看板下钻行")
public class BoardTaskOpsRowVO {

    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "类型")
    private String taskType;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "进度0-100")
    private Integer progress;

    @Schema(description = "负责人")
    private String ownerName;

    @Schema(description = "执行人（逗号拼接）")
    private String assigneeNames;

    @Schema(description = "计划截止")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planEndTime;

    @Schema(description = "实际完成")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    @Schema(description = "是否逾期（未完成）")
    private boolean overdue;

    @Schema(description = "已超时天数（未完成逾期时）")
    private Integer overdueDays;

    @Schema(description = "时效标签：ON_TIME/EARLY/LATE")
    private String timingTag;

    @Schema(description = "相对计划的天数差：负=提前，0=按期，正=超时")
    private Integer timingDays;

    @Schema(description = "时效文案，如 按期/提前3天/超时2天")
    private String timingLabel;
}
