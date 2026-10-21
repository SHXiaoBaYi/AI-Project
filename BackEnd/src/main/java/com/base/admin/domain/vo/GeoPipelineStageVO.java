package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI成文流水线阶段")
public class GeoPipelineStageVO {

    @Schema(description = "阶段编码")
    private String code;

    @Schema(description = "阶段名")
    private String name;

    @Schema(description = "pending|running|done|skipped|failed")
    private String status;

    @Schema(description = "说明")
    private String message;
}
