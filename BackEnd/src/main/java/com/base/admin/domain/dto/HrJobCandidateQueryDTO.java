package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "岗位看板候选人抽屉查询")
public class HrJobCandidateQueryDTO {

    @NotNull(message = "请选择岗位")
    @Schema(description = "招聘需求ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long requisitionId;

    @Schema(description = "漏斗：PENDING未处理 / INTERVIEWING约面中 / ONBOARDED已入职"
            + " / TODAY_SCREEN今日筛选 / TODAY_PHONE今日电话 / TODAY_INVITE今日约面"
            + " / TODAY_INTERVIEW今日面试 / TODAY_INTERVIEW_PASS今日通过面试"
            + " / BG_CHECK背调中 / PENDING_ONBOARD待入职；空则全部")
    private String funnelStatus;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
