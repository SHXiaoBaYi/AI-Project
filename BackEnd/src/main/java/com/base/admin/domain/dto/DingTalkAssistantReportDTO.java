package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "日程助手：跟对方汇报工作")
public class DingTalkAssistantReportDTO {

    @NotNull(message = "请选择汇报对象")
    @Schema(description = "汇报对象系统用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long targetUserId;

    @NotNull(message = "请选择开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-23 14:00:00")
    private LocalDateTime startTime;

    @Min(15)
    @Max(240)
    @Schema(description = "时长（分钟）", example = "60")
    private Integer durationMin;

    @Schema(description = "任务/日程标题，可空", example = "工作汇报")
    private String title;

    @Schema(description = "说明，可空")
    private String content;

    @Schema(description = "地点，可空", example = "线上")
    private String location;
}
