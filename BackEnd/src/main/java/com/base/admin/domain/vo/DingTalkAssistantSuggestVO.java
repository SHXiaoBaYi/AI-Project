package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "日程助手建议结果")
public class DingTalkAssistantSuggestVO {

    @Schema(description = "目标用户ID", example = "1")
    private Long targetUserId;

    @Schema(description = "目标用户昵称", example = "张三")
    private String targetNickname;

    @Schema(description = "目标用户名", example = "zhangsan")
    private String targetUsername;

    @Schema(description = "期望时长（分钟）", example = "60")
    private Integer durationMin;

    @Schema(description = "查询失败或绑定问题时的错误说明")
    private String error;

    @Schema(description = "给用户看的建议文案")
    private String adviceText;

    @Schema(description = "按天分组的推荐时段（每段=期望时长）")
    private List<DayGroup> dayGroups = new ArrayList<>();

    @Schema(description = "连续空闲窗（原始），兼容用")
    private List<FreeWindow> freeWindows = new ArrayList<>();

    @Schema(description = "可附带的操作")
    private List<Action> actions = new ArrayList<>();

    @Data
    @Schema(description = "某一天的推荐时段")
    public static class DayGroup {
        @Schema(description = "日期 yyyy-MM-dd", example = "2026-09-23")
        private String day;

        @Schema(description = "Tab 文案", example = "09-23 周二")
        private String dayLabel;

        @Schema(description = "当天按期望时长切出的可约时段")
        private List<SlotOption> slots = new ArrayList<>();
    }

    @Data
    @Schema(description = "一个可约时段（长度=期望时长）")
    public static class SlotOption {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @Schema(description = "开始", example = "2026-09-23 14:00:00")
        private LocalDateTime start;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @Schema(description = "结束（开始+期望时长）", example = "2026-09-23 15:00:00")
        private LocalDateTime end;

        @Schema(description = "展示文案", example = "14:00–15:00")
        private String label;
    }

    @Data
    @Schema(description = "一段连续空闲")
    public static class FreeWindow {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @Schema(description = "开始", example = "2026-09-23 14:00:00")
        private LocalDateTime start;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @Schema(description = "结束", example = "2026-09-23 15:30:00")
        private LocalDateTime end;

        @Schema(description = "可用分钟数", example = "90")
        private Integer availableMin;

        @Schema(description = "展示文案", example = "09-23 14:00–15:30（90 分钟）")
        private String label;
    }

    @Data
    @Schema(description = "助手附带操作")
    public static class Action {
        @Schema(description = "动作类型", example = "CREATE_MEETING")
        private String type;

        @Schema(description = "按钮文案", example = "邀请他参加会议")
        private String label;

        @Schema(description = "简短说明")
        private String hint;

        @Schema(description = "前端执行所需参数")
        private Map<String, Object> payload = new LinkedHashMap<>();
    }
}
