package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "候选人面试评价")
public class HrInterviewReviewVO {

    @Schema(description = "面试官评价记录 ID；联合评价为空")
    private Long id;

    @Schema(description = "投递 ID")
    private Long applicationId;

    @Schema(description = "面试官用户 ID；联合评价为空")
    private Long interviewerUserId;

    @Schema(description = "关联邀约 ID，可空")
    private Long inviteId;

    @Schema(description = "INTERVIEW面试官评价 JOINT联合评价")
    private String kind;

    @Schema(description = "轮次")
    private Integer roundNo;

    @Schema(description = "轮次名称")
    private String roundName;

    @Schema(description = "评价人")
    private String interviewerName;

    @Schema(description = "结论 PASS/FAIL/PENDING")
    private String conclusion;

    @Schema(description = "未通过原因")
    private String failReason;

    @Schema(description = "评语")
    private String comment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "评价时间")
    private LocalDateTime interviewedAt;
}
