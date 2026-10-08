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
@Schema(description = "候选人全流程详情")
public class HrPipelineDetailVO {

    private Long applicationId;
    private Long requisitionId;
    private String displayName;
    private String phone;
    private String email;
    private String jobName;
    private String currentStage;
    private String stageName;
    private String phase;
    private String phaseLabel;
    private String screenResult;
    private String resumeName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate submittedAt;

    private PhoneScreen phoneScreen;
    private OfferInfo offer;
    private OnboardInfo onboard;
    private List<TimelineItem> timeline = new ArrayList<>();
    private List<InterviewItem> interviews = new ArrayList<>();
    private List<DocItem> documents = new ArrayList<>();

    @Data
    @Schema(description = "电话沟通")
    public static class PhoneScreen {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime calledAt;
        private String result;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime interviewAt;
        private String rejectReason;
        private String remark;
    }

    @Data
    @Schema(description = "Offer")
    public static class OfferInfo {
        private BigDecimal salaryAmount;
        private String status;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime offeredAt;
    }

    @Data
    @Schema(description = "入职")
    public static class OnboardInfo {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate onboardDate;
        private String probationStatus;
    }

    @Data
    @Schema(description = "时间线节点")
    public static class TimelineItem {
        private String stageCode;
        private String stageName;
        private String source;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime eventAt;
        private String summary;
    }

    @Data
    @Schema(description = "面试记录")
    public static class InterviewItem {
        private Integer roundNo;
        private String roundName;
        private String interviewerName;
        private String conclusion;
        private String failReason;
        private String comment;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime interviewedAt;
    }

    @Data
    @Schema(description = "相关文档")
    public static class DocItem {
        private Long id;
        private String kind;
        private String fileName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
        private boolean downloadable;
    }
}
