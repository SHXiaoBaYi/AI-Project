package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "人才库列表行")
public class HrTalentVO {

    @Schema(description = "人才ID")
    private Long candidateId;

    @Schema(description = "姓名")
    private String displayName;

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "年龄", nullable = true)
    private Integer age;

    @Schema(description = "学历")
    private String education;

    @Schema(description = "基本信息摘要")
    private String basicInfo;

    @Schema(description = "人选编号")
    private String candidateNo;

    @Schema(description = "目前职位")
    private String currentTitle;

    @Schema(description = "目前公司")
    private String currentCompany;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "工作年限")
    private String workYears;

    @Schema(description = "期望薪资")
    private String expectedSalary;

    @Schema(description = "技能标签")
    private String skillTags;

    @Schema(description = "学校")
    private String schoolName;

    @Schema(description = "专业")
    private String major;

    @Schema(description = "院校标签")
    private String schoolTags;

    @Schema(description = "在库状态 POOL/IN_PROCESS/ONBOARDED/REACTIVATABLE")
    private String poolStatus;

    @Schema(description = "在库状态名称")
    private String poolStatusName;

    @Schema(description = "有效投递数")
    private Integer applicationCount;

    @Schema(description = "当前用于操作的投递。优先流程中的岗位投递")
    private Long activeApplicationId;

    @Schema(description = "当前投递的岗位")
    private Long activeRequisitionId;

    @Schema(description = "当前岗位名称")
    private String activeJobName;

    @Schema(description = "当前阶段码")
    private String activeStage;

    @Schema(description = "当前阶段名称")
    private String activeStageName;

    @Schema(description = "流程中且尚未处理时为 true")
    private Boolean pending;

    @Schema(description = "未处理时的剩余小时，超时为负数", nullable = true)
    private Double remainingHours;

    @Schema(description = "最近联系时间")
    private String lastContactAt;

    @Schema(description = "下次跟进日期")
    private String nextFollowAt;

    @Schema(description = "当前投递的简历文件名")
    private String resumeName;

    @Schema(description = "当前投递 AI 分", nullable = true)
    private Integer aiScore;

    @Schema(description = "当前投递 AI 份数")
    private Integer aiCount;

    @Schema(description = "全部有效投递 ID，移入黑名单用")
    private List<Long> applicationIds = new ArrayList<>();

    @Schema(description = "作品集")
    private List<HrCandidatePortfolioVO> portfolios = new ArrayList<>();
}
