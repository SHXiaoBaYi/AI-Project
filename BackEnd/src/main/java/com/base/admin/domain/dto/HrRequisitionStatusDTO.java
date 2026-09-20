package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "招聘需求状态变更")
public class HrRequisitionStatusDTO {

    @NotBlank
    @Schema(description = "OPEN招聘中 PAUSED暂缓 ARCHIVED归档 DONE已完成 STOPPED停止招聘")
    private String status;
}
