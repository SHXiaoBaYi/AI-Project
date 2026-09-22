package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "日程助手：查询某人闲忙并给出约谈建议")
public class DingTalkAssistantSuggestDTO {

    @NotNull(message = "请选择要查询的同事")
    @Schema(description = "目标系统用户ID（须已绑定钉钉）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long targetUserId;

    @NotNull(message = "请选择开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "查询开始时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-22 09:00:00")
    private LocalDateTime startTime;

    @NotNull(message = "请选择结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "查询结束时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-26 18:00:00")
    private LocalDateTime endTime;

    @Min(value = 15, message = "时长至少 15 分钟")
    @Max(value = 240, message = "时长最多 240 分钟")
    @Schema(description = "期望连续空闲时长（分钟），默认 60", example = "60")
    private Integer durationMin;
}
