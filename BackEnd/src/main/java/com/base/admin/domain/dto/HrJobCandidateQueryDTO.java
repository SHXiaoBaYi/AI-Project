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

    @Schema(description = "漏斗状态：PENDING未处理 / INTERVIEWING约面中 / ONBOARDED已入职，空则全部")
    private String funnelStatus;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
