package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "GEO风格包保存")
public class GeoArticleStylePackDTO {

    @Schema(description = "ID，更新时必填")
    private Long id;

    @NotBlank
    @Schema(description = "编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhihu_explain")
    private String code;

    @NotBlank
    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "适用平台提示 zhihu/toutiao/...")
    private String platformHint;

    @Schema(description = "语气与节奏")
    private String toneGuide;

    @Schema(description = "结构说明")
    private String structureGuide;

    @Schema(description = "可摘句式 JSON 数组字符串")
    private String samplePhrases;

    @Schema(description = "品牌露出软约束")
    private String brandGuide;

    @Schema(description = "1启用 0停用")
    private Integer enabled;

    @Schema(description = "排序")
    private Integer sortOrder;
}
