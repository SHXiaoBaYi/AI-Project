package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "任务新增/修改")
public class SysTaskDTO {

    @Schema(description = "主键（修改必传）", nullable = true)
    private Long id;

    @NotBlank(message = "任务标题不能为空")
    @Schema(description = "任务标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "任务说明")
    private String content;

    @Schema(description = "任务类型", example = "日常")
    private String taskType;

    @Schema(description = "优先级 1低2中3高4紧急", example = "2")
    private Integer priority;

    @Schema(description = "状态", example = "待处理")
    private String status;

    @Schema(description = "进度0-100", example = "0")
    private Integer progress;

    @NotNull(message = "负责人不能为空")
    @Schema(description = "负责人用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ownerUserId;

    @NotEmpty(message = "至少指定一名执行人")
    @Schema(description = "执行人用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> assigneeUserIds;

    @Schema(description = "计划开始")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planStartTime;

    @Schema(description = "计划截止")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planEndTime;

    @Schema(description = "关联业务类型")
    private String bizType;

    @Schema(description = "关联业务ID")
    private Long bizId;

    @Schema(description = "关联业务摘要")
    private String bizTitle;

    @Schema(description = "备注")
    private String remark;
}
