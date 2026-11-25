package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "人才库分页筛选")
public class HrTalentQueryDTO {

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;

    @Schema(description = "指定人才，档案与权限校验用")
    private Long candidateId;

    @Schema(description = "姓名，模糊")
    private String candidateName;

    @Schema(description = "手机号，片段匹配")
    private String phone;

    @Schema(description = "关键词：姓名、编号、手机、公司、职位、学校、专业、标签、城市、学历")
    private String keyword;

    @Schema(description = "目前公司，模糊")
    private String company;

    @Schema(description = "目前职位，模糊")
    private String title;

    @Schema(description = "学校，模糊")
    private String school;

    @Schema(description = "标签，模糊")
    private String tags;

    @Schema(description = "在库状态 POOL/IN_PROCESS/ONBOARDED/REACTIVATABLE")
    private String poolStatus;

    @Schema(description = "多个在库状态，任一命中。人才库默认页传 POOL 与 REACTIVATABLE")
    private List<String> poolStatuses;

    @Schema(description = "曾投递或当前流程中的岗位")
    private Long requisitionId;

    @Schema(description = "渠道，命中任一投递")
    private String channelCode;

    @Schema(description = "阶段，命中任一投递的当前阶段")
    private String stageCode;

    @Schema(description = "简历提交人用户，命中任一投递")
    private Long submitterUserId;

    @Schema(description = "招聘负责人，命中任一岗位")
    private List<Long> ownerUserIds;

    @Schema(description = "投递日期起，命中任一投递")
    private LocalDate startDate;

    @Schema(description = "投递日期止，命中任一投递")
    private LocalDate endDate;
}
