package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @Schema(description = "日程之间缓存（分钟）")
    private int bufferMin = 15;

    @Schema(description = "时段表格子时长（分钟）")
    private int slotMin = 30;

    @Schema(description = "向前推荐天数")
    private int lookAheadDays = 14;

    @Schema(description = "单次推荐条数上限")
    private int recommendLimit = 8;

    @Schema(description = "是否允许秘书机器人读取并推荐（定数：永远为 true）")
    private boolean secretaryEnabled = true;

    @Schema(description = "机器人提示")
    private String robotHint;

    @Schema(description = "工作日开始 HH:mm")
    private String workStart = "09:30";

    @Schema(description = "工作日结束 HH:mm")
    private String workEnd = "18:30";

    @Schema(description = "早晨时段开始 HH:mm（一般不安排，除非很紧急重要）")
    private String morningStart = "09:30";

    @Schema(description = "早晨时段结束 HH:mm")
    private String morningEnd = "11:00";

    @Schema(description = "上午时段开始 HH:mm（集中安排部门会议）")
    private String forenoonStart = "11:00";

    @Schema(description = "上午时段结束 HH:mm")
    private String forenoonEnd = "13:00";

    @Schema(description = "午休时段开始 HH:mm（吃饭时间，尽量不安排工作）")
    private String lunchStart = "13:00";

    @Schema(description = "午休时段结束 HH:mm")
    private String lunchEnd = "14:00";

    @Schema(description = "不安排任何日程的时段")
    private List<Window> blockedWindows = new ArrayList<>();

    @Schema(description = "部门会议时段规则")
    private List<Window> deptMeetingWindows = new ArrayList<>();

    @Schema(description = "会议优先级设置")
    private MeetingPriority meetingPriority = new MeetingPriority();

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

    @Data
    @Schema(description = "会议优先级")
    public static class MeetingPriority {
        @Schema(description = "优先级顺序，越靠前越高：SUPERVISOR/CORE_PROJECT/OTHER_BIZ/CROSS_DEPT")
        private List<String> priorityOrder = new ArrayList<>();

        @JsonAlias({"supervisorDingTalkUserIds"})
        @Schema(description = "直接上级：本地用户昵称/姓名，也可手填不存在的人名")
        private List<String> supervisorNames = new ArrayList<>();

        @Schema(description = "核心项目部ID")
        private List<Long> coreProjectDeptIds = new ArrayList<>();

        @Schema(description = "其他业务部门ID")
        private List<Long> otherBizDeptIds = new ArrayList<>();

        @Schema(description = "结构化偏好时间规则（多个）")
        private List<PreferRule> preferRules = new ArrayList<>();

        @Schema(description = "面试频次")
        private InterviewFreq interviewFreq = new InterviewFreq();
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
        @Schema(description = "小岗位名称")
        private List<String> smallJobs = new ArrayList<>();

        @Schema(description = "小岗位每天合计下限")
        private int smallJobsDailyMin = 2;

        @Schema(description = "小岗位每天合计上限")
        private int smallJobsDailyMax = 3;

        @Schema(description = "重要岗位名称")
        private List<String> importantJobs = new ArrayList<>();

        @Schema(description = "重要岗位每天最多")
        private int importantJobsDailyMax = 2;
    }
}
