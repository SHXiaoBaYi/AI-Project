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

    @Schema(description = "日程之间缓存（分钟）", example = "15")
    private Integer bufferMin;

    @Schema(description = "时段表格子时长（分钟）", example = "30")
    private Integer slotMin;

    @Schema(description = "向前推荐天数（供机器人读取）", example = "14")
    private Integer lookAheadDays;

    @Schema(description = "单次推荐条数上限（供机器人读取）", example = "8")
    private Integer recommendLimit;

    @NotNull
    @Schema(description = "是否允许秘书机器人读取本规则并推荐（定数：永远 true，入参忽略）", example = "true")
    private Boolean secretaryEnabled;

    @Schema(description = "机器人拒约/无空档提示")
    private String robotHint;

    @Schema(description = "工作日开始 HH:mm（可空，默认取早晨开始）", example = "09:30")
    private String workStart;

    @Schema(description = "工作日结束 HH:mm", example = "18:30")
    private String workEnd;

    @Schema(description = "早晨时段开始 HH:mm", example = "09:30")
    private String morningStart;

    @Schema(description = "早晨时段结束 HH:mm", example = "11:00")
    private String morningEnd;

    @Schema(description = "上午时段开始 HH:mm（集中安排部门会议）", example = "11:00")
    private String forenoonStart;

    @Schema(description = "上午时段结束 HH:mm", example = "13:00")
    private String forenoonEnd;

    @Schema(description = "午休时段开始 HH:mm", example = "13:00")
    private String lunchStart;

    @Schema(description = "午休时段结束 HH:mm", example = "14:00")
    private String lunchEnd;

    @Valid
    @Schema(description = "不安排任何日程的时段")
    private List<Window> blockedWindows = new ArrayList<>();

    @Valid
    @Schema(description = "部门会议时段规则")
    private List<Window> deptMeetingWindows = new ArrayList<>();

    @Valid
    @Schema(description = "会议优先级设置")
    private MeetingPriority meetingPriority;

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

    @Data
    @Schema(description = "会议优先级")
    public static class MeetingPriority {
        @Schema(description = "优先级顺序，越靠前越高：SUPERVISOR/CORE_PROJECT/OTHER_BIZ/CROSS_DEPT")
        private List<String> priorityOrder = new ArrayList<>();

        @Schema(description = "直接上级：本地用户昵称/姓名（多个），也可手填不存在的人名")
        private List<String> supervisorNames = new ArrayList<>();

        @Schema(description = "核心项目部ID（多个）")
        private List<Long> coreProjectDeptIds = new ArrayList<>();

        @Schema(description = "其他业务部门ID（多个）")
        private List<Long> otherBizDeptIds = new ArrayList<>();

        @Valid
        @Schema(description = "结构化偏好时间规则（多个）")
        private List<PreferRule> preferRules = new ArrayList<>();

        @Valid
        @Schema(description = "面试频次")
        private InterviewFreq interviewFreq;
    }

    @Data
    @Schema(description = "偏好时间规则：将某类事务安排在某时段")
    public static class PreferRule {
        @Schema(description = "场景：EXTERNAL_REPORT/INTERNAL_MEETING/EXTERNAL_MEETING/INTERVIEW/ONE_ON_ONE/CUSTOM")
        private String scene;

        @Schema(description = "自定义场景名称，scene=CUSTOM 时使用")
        private String sceneLabel;

        @Schema(description = "时段类型：PERIOD=上午/下午，RANGE=自定义起止")
        private String slotKind;

        @Schema(description = "PERIOD 时：MORNING / AFTERNOON")
        private String period;

        @Schema(description = "RANGE 时开始 HH:mm")
        private String startTime;

        @Schema(description = "RANGE 时结束 HH:mm")
        private String endTime;
    }

    @Data
    @Schema(description = "面试频次")
    public static class InterviewFreq {
        @Schema(description = "小岗位名称（多个）")
        private List<String> smallJobs = new ArrayList<>();

        @Schema(description = "小岗位每天合计下限", example = "2")
        private Integer smallJobsDailyMin;

        @Schema(description = "小岗位每天合计上限", example = "3")
        private Integer smallJobsDailyMax;

        @Schema(description = "重要岗位名称（多个）")
        private List<String> importantJobs = new ArrayList<>();

        @Schema(description = "重要岗位每天最多", example = "2")
        private Integer importantJobsDailyMax;
    }
}
