package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "单岗位简历全流程看板")
public class HrPipelineBoardVO {

    @Schema(description = "需求ID")
    private Long requisitionId;

    @Schema(description = "岗位名称")
    private String jobName;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "四阶段候选人")
    private List<PhaseColumn> phases = new ArrayList<>();

    @Data
    @Schema(description = "一个流程阶段列")
    public static class PhaseColumn {
        @Schema(description = "SCREEN/PHONE/INTERVIEW/ONBOARD")
        private String phase;

        @Schema(description = "阶段标题")
        private String phaseLabel;

        @Schema(description = "人数")
        private Integer count;

        @Schema(description = "候选人卡片")
        private List<CandidateCard> candidates = new ArrayList<>();
    }

    @Data
    @Schema(description = "流程中的候选人卡片")
    public static class CandidateCard {
        private Long applicationId;
        private String displayName;
        private String phone;
        private String email;
        private String currentStage;
        private String stageName;
        private String phase;
        private String screenResult;
        private String phoneResult;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime phoneCalledAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime phoneInterviewAt;
        private String phoneRejectReason;
        private String resumeName;
        private Integer portfolioCount;
        private BigDecimal salaryAmount;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate onboardDate;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate submittedAt;
        private String latestInterviewConclusion;
        private String latestInterviewerName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime latestInterviewAt;
    }
}
