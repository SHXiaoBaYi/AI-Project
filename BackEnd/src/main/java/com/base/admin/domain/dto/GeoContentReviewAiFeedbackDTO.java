package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "AI预审准/不准反馈")
public class GeoContentReviewAiFeedbackDTO {

    @NotNull(message = "审核轮次ID不能为空")
    @Schema(description = "审核轮次ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long reviewId;

    @NotBlank(message = "请选择反馈")
    @Schema(description = "accurate=准 inaccurate=不准", requiredMode = Schema.RequiredMode.REQUIRED, example = "accurate")
    private String feedback;

    @Schema(description = "补充说明")
    private String remark;
}
