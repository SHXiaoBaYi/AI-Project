package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "发起面试邀约")
public class HrInviteCreateDTO {

    @NotNull
    @Schema(description = "投递ID")
    private Long applicationId;

    @NotNull
    @Min(1)
    @Max(5)
    @Schema(description = "轮次，1起")
    private Integer roundNo;

    @Schema(description = "面试官用户ID，修改单条时使用")
    private Long interviewerUserId;

    @Schema(description = "面试官用户ID，新增时可多人，每人一条日程")
    private java.util.List<Long> interviewerUserIds;

    @Schema(description = "抄送人用户ID，可多人；不传则按招聘需求该轮抄送配置")
    private java.util.List<Long> ccUserIds;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间，每天 09:30～17:30，半小时整点")
    private LocalDateTime interviewAt;

    @Min(15)
    @Max(240)
    @Schema(description = "时长分钟，默认60")
    private Integer durationMin;

    @Schema(description = "地点或会议方式")
    private String location;
}
