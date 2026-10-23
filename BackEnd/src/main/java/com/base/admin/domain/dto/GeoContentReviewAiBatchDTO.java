package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量触发AI预审")
public class GeoContentReviewAiBatchDTO {

    @NotEmpty(message = "请选择审核轮次")
    @Schema(description = "审核轮次ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> reviewIds;
}
