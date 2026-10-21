package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "GEO文章骨架保存")
public class GeoArticleSkeletonDTO {

    @Schema(description = "ID，更新时必填")
    private Long id;

    @NotBlank
    @Schema(description = "编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "qa_sections_v1")
    private String code;

    @NotBlank
    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "章节骨架 JSON")
    private String sectionSchema;

    @Schema(description = "1启用 0停用")
    private Integer enabled;

    @Schema(description = "排序")
    private Integer sortOrder;
}
