package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "面试未通过原因")
public class HrFailReasonDTO {

    @Schema(description = "主键，新增不传", nullable = true)
    private Long id;

    @NotBlank(message = "未通过原因不能为空")
    @Schema(description = "未通过原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "候选人能力不符")
    private String name;

    @Schema(description = "排序，越小越靠前", example = "1")
    private Integer sortNo;
}
