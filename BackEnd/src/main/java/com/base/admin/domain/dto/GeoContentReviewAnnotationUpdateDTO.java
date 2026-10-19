package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "修改审核划词批注内容")
public class GeoContentReviewAnnotationUpdateDTO {

    @NotNull
    @Schema(description = "批注ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @NotBlank
    @Schema(description = "批注内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "请改成与目标问题一致的数据")
    private String commentText;
}
