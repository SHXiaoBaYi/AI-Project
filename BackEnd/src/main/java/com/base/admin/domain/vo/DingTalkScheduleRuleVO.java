package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "本人日程规则（配置页 + 秘书机器人读取）")
public class DingTalkScheduleRuleVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "是否启用个人规则")
    private boolean enabled;

    @Schema(description = "是否禁止法定节假日")
    private boolean denyHolidays = true;

    @Schema(description = "场次间隔缓冲（分钟）")
    private int bufferMin;

    @Schema(description = "向前推荐天数")
    private int lookAheadDays = 14;

    @Schema(description = "单次推荐条数上限")
    private int recommendLimit = 8;

    @Schema(description = "是否允许秘书机器人读取并推荐")
    private boolean secretaryEnabled = true;

    @Schema(description = "机器人提示")
    private String robotHint;

    @Schema(description = "不安排任何日程的时段")
    private List<Window> blockedWindows = new ArrayList<>();

    @Schema(description = "各动作卡片偏好")
    private List<ActionPref> actionPrefs = new ArrayList<>();

    @Data
    @Schema(description = "时间窗")
    public static class Window {
        private List<Integer> weekdays = new ArrayList<>();
        private String startTime;
        private String endTime;
    }

    @Data
    @Schema(description = "动作偏好")
    public static class ActionPref {
        @Schema(description = "interview / meeting / report")
        private String action;

        @Schema(description = "动作展示名")
        private String actionLabel;

        private boolean enabled = true;

        private int preferDurationMin = 60;

        private Integer maxDurationMin;

        private List<Window> windows = new ArrayList<>();
    }
}
