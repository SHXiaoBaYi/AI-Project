package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "面试记录")
public class HrInterviewRecordDTO {

    @Schema(description = "记录ID，新增为空")
    private Long id;

    @NotNull
    @Schema(description = "候选人投递")
    private Long applicationId;

    @Schema(description = "招聘需求，可空，默认取投递上的需求")
    private Long requisitionId;

    @Schema(description = "关联邀约")
    private Long inviteId;

    @NotNull
    @Schema(description = "轮次")
    private Integer roundNo;

    @Schema(description = "面试官，修改单条时使用")
    private Long interviewerUserId;

    @Schema(description = "面试官，按邀约生成时可多人，每人一条记录")
    private java.util.List<Long> interviewerUserIds;

    @NotBlank
    @Schema(description = "结论 PASS通过 FAIL未通过 PENDING待定")
    private String conclusion;

    @Schema(description = "评语")
    private String comment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "面试时间")
    private LocalDateTime interviewedAt;
}
