package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "机器人 H5 意图执行结果")
public class DingTalkIntentExecuteVO {

    @Schema(description = "done=已完成；open_invite_form/open_meeting_form/open_report_form=打开系统表单")
    private String status;

    @Schema(description = "结果文案")
    private String message;

    @Schema(description = "待选候选人（status=need_candidate）")
    private List<Candidate> candidates = new ArrayList<>();

    @Schema(description = "邀约表单预填：面试官用户 ID（多人时为首位）")
    private Long targetUserId;

    @Schema(description = "多人共同空闲：全部目标用户 ID")
    private List<Long> targetUserIds = new ArrayList<>();

    @Schema(description = "邀约表单预填：面试官昵称（多人时为「A、B」）")
    private String targetNickname;

    @Schema(description = "多人昵称列表")
    private List<String> targetNicknames = new ArrayList<>();

    @Schema(description = "邀约表单预填：开始时间 yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Schema(description = "邀约表单预填：时长分钟")
    private Integer durationMin;

    @Schema(description = "查询里提到的岗位（仅提示，不锁定）")
    private String jobName;

    @Schema(description = "机器人推荐时段（表单内单选，默认第一项）")
    private List<SlotOption> slots = new ArrayList<>();

    @Data
    @Schema(description = "候选人选项")
    public static class Candidate {
        @Schema(description = "展示名")
        private String name;
        @Schema(description = "岗位")
        private String jobName;
        @Schema(description = "点选后继续执行的 invite 票据")
        private String ticket;
    }

    @Data
    @Schema(description = "推荐时段")
    public static class SlotOption {
        @Schema(description = "开始时间 yyyy-MM-dd HH:mm:ss")
        private String start;
        @Schema(description = "时长分钟")
        private Integer durationMin;
        @Schema(description = "展示文案")
        private String label;
    }
}
