package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "GEO话题新增/修改")
public class GeoTopicDTO {

    @Schema(description = "话题ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotBlank(message = "话题名称不能为空")
    @Schema(description = "话题名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "送礼")
    private String topicName;

    @Schema(description = "开始优化时间", example = "8月第2周")
    private String optimizeWeek;

    @Schema(description = "备注")
    private String remark;
}
