package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "GEO平台新增/修改")
public class GeoPlatformDTO {

    @Schema(description = "平台ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotBlank(message = "平台名称不能为空")
    @Schema(description = "平台名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "豆包")
    private String platformName;

    @NotBlank(message = "平台类型不能为空")
    @Schema(description = "平台类型（AI平台/内容发布平台）", requiredMode = Schema.RequiredMode.REQUIRED, example = "AI平台")
    private String platformType;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
