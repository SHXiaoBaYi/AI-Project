package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "助手日程修改")
public class DingTalkAssistantScheduleUpdateDTO {

    @NotNull(message = "缺少记录ID")
    @Schema(description = "记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @NotBlank(message = "请填写主题")
    @Schema(description = "主题", requiredMode = Schema.RequiredMode.REQUIRED, example = "周会")
    private String title;

    @NotNull(message = "请选择开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-23 14:00:00")
    private LocalDateTime startTime;

    @Min(15)
    @Max(240)
    @Schema(description = "时长（分钟）", example = "60")
    private Integer durationMin;

    @Schema(description = "地点", example = "3楼会议室")
    private String location;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否钉钉视频会议", example = "true")
    private Boolean onlineMeeting;
}
