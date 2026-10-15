package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "AI 生成文章草稿请求")
public class GeoContentAiGenerateDTO {

    @NotNull
    @Schema(description = "投放主表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long placementId;

    @Schema(description = "平台文章 itemId；空=写统一稿", example = "12", nullable = true)
    private Long itemId;

    @Schema(description = "AI 厂商编码，空则用默认已配置厂商", example = "tongyi")
    private String provider;

    @Schema(description = "用户补充提示词（风格、角度、必提点等），与话题/目标问题一并约束生成",
            example = "偏科普口吻，突出有机双认证，控制在1200字内")
    private String userPrompt;
}
