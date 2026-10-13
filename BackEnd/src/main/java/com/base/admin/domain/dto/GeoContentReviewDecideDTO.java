package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "审核人通过或退回")
public class GeoContentReviewDecideDTO {

    @NotNull
    @Schema(description = "审核轮次ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long reviewId;

    @NotBlank
    @Schema(description = "approve=通过 reject=退回", requiredMode = Schema.RequiredMode.REQUIRED, example = "approve")
    private String action;

    @Schema(description = "意见（退回必填）", example = "标题不够清晰")
    private String comment;
}
