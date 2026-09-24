package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "秘书推荐可约时段请求（仅本人）")
public class DingTalkScheduleRecommendDTO {

    @Schema(description = "动作：默认 interview", example = "interview")
    private String action;

    @Schema(description = "时长分钟；空则用规则里的 preferDurationMin", example = "60", nullable = true)
    private Integer durationMin;
}
