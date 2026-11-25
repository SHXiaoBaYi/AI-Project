package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "人才档案")
public class HrTalentProfileVO {

    @Schema(description = "人才概要，与列表行相同")
    private HrTalentVO talent;

    @Schema(description = "全部投递")
    private List<Application> applications = new ArrayList<>();

    @Schema(description = "跟进备注，新的在前")
    private List<Note> notes = new ArrayList<>();

    @Schema(description = "电话沟通")
    private List<Phone> phones = new ArrayList<>();

    @Schema(description = "面试评价摘要")
    private List<Interview> interviews = new ArrayList<>();

    @Schema(description = "Offer")
    private List<Offer> offers = new ArrayList<>();

    @Data
    @Schema(description = "人才的一条投递")
    public static class Application {
        @Schema(description = "投递ID")
        private Long applicationId;
        @Schema(description = "岗位ID")
        private Long requisitionId;
        @Schema(description = "岗位名称，未挂岗为空")
        private String jobName;
        @Schema(description = "渠道码")
        private String channelCode;
        @Schema(description = "渠道名称")
        private String channelName;
        @Schema(description = "阶段码")
        private String stageCode;
        @Schema(description = "阶段名称")
        private String stageName;
        @Schema(description = "是否终态")
        private Boolean terminal;
        @Schema(description = "投递日期")
        private String submittedAt;
        @Schema(description = "提交人")
        private String submitterName;
        @Schema(description = "简历文件名")
        private String resumeName;
    }

    @Data
    @Schema(description = "跟进备注")
    public static class Note {
        @Schema(description = "备注ID")
        private Long id;
        @Schema(description = "内容")
        private String content;
        @Schema(description = "下次跟进日期")
        private String nextFollowAt;
        @Schema(description = "记录人")
        private String operatorName;
        @Schema(description = "记录时间")
        private String createTime;
    }

    @Data
    @Schema(description = "电话沟通")
    public static class Phone {
        @Schema(description = "投递ID")
        private Long applicationId;
        @Schema(description = "岗位名称")
        private String jobName;
        @Schema(description = "沟通时间")
        private String calledAt;
        @Schema(description = "PASS/FAIL")
        private String result;
        @Schema(description = "不合适原因")
        private String rejectReason;
        @Schema(description = "备注")
        private String remark;
    }

    @Data
    @Schema(description = "面试评价摘要")
    public static class Interview {
        @Schema(description = "投递ID")
        private Long applicationId;
        @Schema(description = "岗位名称")
        private String jobName;
        @Schema(description = "轮次")
        private Integer roundNo;
        @Schema(description = "结论")
        private String conclusion;
        @Schema(description = "面试官")
        private String interviewerName;
        @Schema(description = "面试时间")
        private String interviewedAt;
        @Schema(description = "评语")
        private String comment;
    }

    @Data
    @Schema(description = "Offer")
    public static class Offer {
        @Schema(description = "投递ID")
        private Long applicationId;
        @Schema(description = "岗位名称")
        private String jobName;
        @Schema(description = "状态")
        private String status;
        @Schema(description = "发放时间")
        private String offeredAt;
        @Schema(description = "金额", nullable = true)
        private String salaryAmount;
        @Schema(description = "拒绝原因")
        private String rejectReason;
    }
}
