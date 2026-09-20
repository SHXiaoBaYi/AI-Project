package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "目标到岗选项")
public class HrTargetOptionDTO {

    @Schema(description = "主键，新增不传", nullable = true)
    private Long id;

    @NotBlank(message = "目标到岗不能为空")
    @Schema(description = "目标到岗", requiredMode = Schema.RequiredMode.REQUIRED, example = "尽快")
    private String name;

    @Schema(description = "排序，越小越靠前", example = "1")
    private Integer sortNo;
}
