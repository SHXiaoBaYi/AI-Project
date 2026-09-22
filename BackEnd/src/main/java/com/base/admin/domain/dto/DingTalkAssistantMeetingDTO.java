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
@Schema(description = "日程助手：邀请参加会议（字段对齐钉钉日程）")
public class DingTalkAssistantMeetingDTO {

    @NotNull(message = "请选择会议对象")
    @Schema(description = "被邀请人系统用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long targetUserId;

    @NotBlank(message = "请填写会议主题")
    @Schema(description = "会议主题（钉钉 summary）", requiredMode = Schema.RequiredMode.REQUIRED, example = "周会同步")
    private String title;

    @NotNull(message = "请选择开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-23 14:00:00")
    private LocalDateTime startTime;

    @Min(15)
    @Max(240)
    @Schema(description = "时长（分钟）", example = "60")
    private Integer durationMin;

    @Schema(description = "地点（钉钉 location.displayName）", example = "3楼会议室")
    private String location;

    @Schema(description = "描述（钉钉 description）", example = "同步本周进度")
    private String description;

    @Schema(description = "是否开启钉钉视频会议", example = "true")
    private Boolean onlineMeeting;
}
