package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "保存本人日程规则（仅配置；推荐由秘书机器人执行）")
public class DingTalkScheduleRuleDTO {

    @NotNull
    @Schema(description = "是否启用个人规则（可约/不安排时段）", example = "true")
    private Boolean enabled;

    @NotNull
    @Schema(description = "是否禁止法定节假日", example = "true")
    private Boolean denyHolidays;

    @Schema(description = "场次间隔缓冲（分钟）", example = "15")
    private Integer bufferMin;

    @Schema(description = "向前推荐天数（供机器人读取）", example = "14")
    private Integer lookAheadDays;

    @Schema(description = "单次推荐条数上限（供机器人读取）", example = "8")
    private Integer recommendLimit;

    @NotNull
    @Schema(description = "是否允许秘书机器人读取本规则并推荐", example = "true")
    private Boolean secretaryEnabled;

    @Schema(description = "机器人拒约/无空档提示")
    private String robotHint;

    @Valid
    @Schema(description = "不安排任何日程的时段")
    private List<Window> blockedWindows = new ArrayList<>();

    @Valid
    @Schema(description = "各智能动作卡片的偏好（面试/会议/汇报）")
    private List<ActionPref> actionPrefs = new ArrayList<>();

    @Data
    @Schema(description = "时间窗")
    public static class Window {
        @Schema(description = "星期几 1=周一 … 7=周日")
        private List<Integer> weekdays = new ArrayList<>();

        @Schema(description = "开始 HH:mm")
        private String startTime;

        @Schema(description = "结束 HH:mm")
        private String endTime;
    }

    @Data
    @Schema(description = "单个动作卡片偏好")
    public static class ActionPref {
        @Schema(description = "动作：interview/meeting/report")
        private String action;

        @Schema(description = "是否允许该动作")
        private Boolean enabled;

        @Schema(description = "该动作推荐默认时长")
        private Integer preferDurationMin;

        @Schema(description = "该动作最长时长，空=不限制", nullable = true)
        private Integer maxDurationMin;

        @Valid
        @Schema(description = "该动作喜好可约时段")
        private List<Window> windows = new ArrayList<>();
    }
}
