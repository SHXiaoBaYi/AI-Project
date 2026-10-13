package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "平台文章内部审核动作")
public class GeoContentReviewActionDTO {

    @NotNull
    @Schema(description = "平台文章ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long itemId;

    @NotBlank
    @Schema(description = "动作 approve=通过 revise=退回修改", requiredMode = Schema.RequiredMode.REQUIRED, example = "revise")
    private String action;

    @Schema(description = "审核意见，退回时必填", example = "第二段数据和话题不符")
    private String opinion;
}
