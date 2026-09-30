package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "日程助手：查询某人闲忙并给出约谈建议")
public class DingTalkAssistantSuggestDTO {

    @Schema(description = "目标系统用户ID（须已绑定钉钉）；多人时传第一个，并配合 targetUserIds", example = "1")
    private Long targetUserId;

    @Schema(description = "多人共同空闲：目标用户 ID 列表（含首位）；有值时按交集推荐时段")
    private List<Long> targetUserIds = new ArrayList<>();

    @Schema(description = "用户原话（口语）；有则后端解析意图/岗位/时长/日期", example = "@张三 什么时候有空可以面试一个品牌总监")
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "查询开始时间（可空；空则按对方 lookAheadDays）", example = "2026-09-22 09:00:00")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "查询结束时间（可空）", example = "2026-09-26 18:00:00")
    private LocalDateTime endTime;

    @Min(value = 15, message = "时长至少 15 分钟")
    @Max(value = 240, message = "时长最多 240 分钟")
    @Schema(description = "期望连续空闲时长（分钟）；空则按动作偏好/默认", example = "60")
    private Integer durationMin;

    @Schema(description = "意图动作：interview / meeting / report / busy_query", example = "interview")
    private String action;

    @Schema(description = "招聘岗位名（面试意图时可传）", example = "品牌总监")
    private String jobName;
}
