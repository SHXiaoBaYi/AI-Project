package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "任务批量完成（仅不要求证明附件的类型）")
public class SysTaskBatchCompleteDTO {

    @NotEmpty(message = "请选择至少一条任务")
    @Schema(description = "任务ID列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2]")
    private List<Long> taskIds;

    @Schema(description = "统一完成说明（可选）")
    private String remark;
}
