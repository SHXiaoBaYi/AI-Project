package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "助手日程记录")
public class DingTalkAssistantScheduleVO {

    @Schema(description = "记录ID", example = "1")
    private Long id;

    @Schema(description = "类型 MEETING/REPORT", example = "MEETING")
    private String kind;

    @Schema(description = "类型中文", example = "会议")
    private String kindLabel;

    @Schema(description = "主题", example = "周会")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "地点")
    private String location;

    @Schema(description = "是否视频会议 1/0", example = "1")
    private Integer onlineMeeting;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "时长分钟", example = "60")
    private Integer durationMin;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "创建人用户ID（发起查询/建日程的人）", example = "1")
    private Long querierUserId;

    @Schema(description = "创建人昵称")
    private String querierNickname;

    @Schema(description = "对方用户ID", example = "2")
    private Long targetUserId;

    @Schema(description = "对方昵称")
    private String targetNickname;

    @Schema(description = "查询者钉钉日程ID")
    private String querierEventId;

    @Schema(description = "对方钉钉日程ID")
    private String targetEventId;

    @Schema(description = "关联任务ID", example = "10", nullable = true)
    private Long taskId;

    @Schema(description = "状态 SUCCESS/CANCELLED", example = "SUCCESS")
    private String status;

    @Schema(description = "状态中文", example = "有效")
    private String statusLabel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
