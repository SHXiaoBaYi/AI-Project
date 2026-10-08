package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "单岗位简历流程看板查询")
public class HrPipelineBoardQueryDTO {

    @NotNull
    @Schema(description = "招聘需求ID")
    private Long requisitionId;
}
