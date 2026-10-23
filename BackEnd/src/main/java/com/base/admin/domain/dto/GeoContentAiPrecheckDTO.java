package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "AI 成文后草稿预检（对照审稿知识库，不落审核轮次）")
public class GeoContentAiPrecheckDTO {

    @NotNull(message = "内容投放ID不能为空")
    @Schema(description = "内容投放ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long placementId;

    @Schema(description = "标题")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Schema(description = "Lake/HTML 正文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String body;
}
