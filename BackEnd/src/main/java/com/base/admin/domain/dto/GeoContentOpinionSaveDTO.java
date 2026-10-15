package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "平台文章内部审核意见")
public class GeoContentOpinionSaveDTO {

    @NotNull
    @Schema(description = "平台文章ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long itemId;

    @NotBlank
    @Schema(description = "意见内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "开头补充产品型号")
    private String opinion;
}
