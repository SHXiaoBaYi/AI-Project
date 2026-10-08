package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "按部门分组的岗位信息卡片")
public class HrJobCardVO {

    @Schema(description = "部门分组")
    private List<DeptGroup> groups = new ArrayList<>();

    @Data
    @Schema(description = "一个部门下的岗位卡片")
    public static class DeptGroup {
        @Schema(description = "部门ID，未分配时为空")
        private Long deptId;

        @Schema(description = "部门名称")
        private String deptName;

        @Schema(description = "本组岗位数")
        private Integer jobCount;

        @Schema(description = "岗位卡片")
        private List<JobCard> jobs = new ArrayList<>();
    }

    @Data
    @Schema(description = "岗位信息卡片")
    public static class JobCard {
        @Schema(description = "需求ID")
        private Long id;

        @Schema(description = "岗位名称")
        private String jobName;

        @Schema(description = "薪资范围")
        private String salaryRange;

        @Schema(description = "学历要求")
        private String educationReq;

        @Schema(description = "经验要求")
        private String experienceReq;

        @Schema(description = "技能要求")
        private String skillReq;

        @Schema(description = "简历要求摘要（学历/经验/技能拼接）")
        private String resumeRequirement;

        @Schema(description = "工作内容摘要")
        private String jobSummary;

        @Schema(description = "招聘起始日期")
        private LocalDate receivedDate;

        @Schema(description = "预计到岗说明")
        private String targetText;

        @Schema(description = "预计到岗/入职日期")
        private LocalDate onboardDate;

        @Schema(description = "招聘负责人展示名")
        private String ownerNames;

        @Schema(description = "招聘负责人用户ID")
        private List<Long> ownerUserIds = new ArrayList<>();

        @Schema(description = "部门ID")
        private Long deptId;

        @Schema(description = "部门名称")
        private String deptName;

        @Schema(description = "原始状态 OPEN/DONE/…")
        private String status;

        @Schema(description = "卡片状态 PENDING/ACTIVE/CLOSED")
        private String cardStatus;

        @Schema(description = "卡片状态文案 待招/进行中/已关闭")
        private String cardStatusLabel;

        @Schema(description = "紧急程度 1/2/3")
        private Integer priority;

        @Schema(description = "紧急程度文案")
        private String priorityLabel;

        @Schema(description = "需求人数")
        private Integer headcount;

        @Schema(description = "在招候选人数量（用于区分待招/进行中）")
        private Integer candidateCount;

        @Schema(description = "工作地 SH/XJ")
        private String locationCode;
    }
}
