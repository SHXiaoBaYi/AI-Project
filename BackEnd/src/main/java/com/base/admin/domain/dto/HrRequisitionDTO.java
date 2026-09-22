package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "招聘需求保存")
public class HrRequisitionDTO {

    @Schema(description = "需求ID，新增为空")
    private Long id;

    @NotBlank
    @Schema(description = "岗位名称")
    private String jobName;

    @Schema(description = "岗位职责")
    private String jobDesc;

    @NotBlank
    @Schema(description = "工作地 SH/XJ")
    private String locationCode;

    @Schema(description = "部门")
    private Long deptId;

    @Schema(description = "需求人数")
    private Integer headcount;

    @Schema(description = "目标到岗：紧急-尽快、尽快、7月-尽快、常规节奏持续招聘")
    private String targetText;

    @Schema(description = "优先级 1紧急 2优先 3常规")
    private Integer priority;

    @NotNull
    @Schema(description = "需求接收日")
    private LocalDate receivedDate;

    @Schema(description = "入职日期")
    private LocalDate onboardDate;

    @Schema(description = "负责人用户ID")
    private List<Long> ownerUserIds;

    @Valid
    @Schema(description = "面试流程：从一面起连续的轮次及面试官")
    private List<InterviewRound> interviewRounds;

    @Data
    @Schema(description = "招聘需求的一轮面试")
    public static class InterviewRound {

        @NotNull
        @Schema(description = "轮次，从 1 开始连续")
        private Integer roundNo;

        @Schema(description = "该轮面试官，可多人")
        private java.util.List<Long> interviewerUserIds;

        @Schema(description = "该轮抄送人，可多人")
        private java.util.List<Long> ccUserIds;
    }
}
