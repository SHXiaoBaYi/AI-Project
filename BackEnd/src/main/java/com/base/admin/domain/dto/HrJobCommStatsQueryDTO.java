package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "岗位候选人抽屉沟通统计查询")
public class HrJobCommStatsQueryDTO {

    @NotNull(message = "请选择岗位")
    @Schema(description = "招聘需求ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long requisitionId;
}
