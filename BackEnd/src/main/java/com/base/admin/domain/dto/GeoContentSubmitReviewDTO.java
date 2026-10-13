package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "撰写人发起多人审核")
public class GeoContentSubmitReviewDTO {

    @NotNull
    @Schema(description = "投放主表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long placementId;

    @NotEmpty
    @Schema(description = "审核人用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> reviewerUserIds;

    @Schema(description = "提审说明", example = "请帮忙看下第二段")
    private String remark;
}
