package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "内容投放-AI引用新增/修改")
public class GeoContentPlacementCiteDTO {

    @Schema(description = "引用ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotNull(message = "投放主表ID不能为空")
    @Schema(description = "投放主表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long placementId;

    @Schema(description = "关联平台投放明细ID", example = "1", nullable = true)
    private Long itemId;

    @NotBlank(message = "提问问题不能为空")
    @Schema(description = "提问问题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String askQuestion;

    @NotBlank(message = "AI平台不能为空")
    @Schema(description = "AI平台名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "豆包")
    private String aiPlatform;

    @Schema(description = "引用链接")
    private String citeUrl;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
