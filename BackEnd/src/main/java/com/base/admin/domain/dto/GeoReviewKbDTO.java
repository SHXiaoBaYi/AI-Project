package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "GEO内容审核知识库保存")
public class GeoReviewKbDTO {

    @Schema(description = "主键，更新必填", example = "1")
    private Long id;

    @NotBlank(message = "分类不能为空")
    @Schema(description = "分类", requiredMode = Schema.RequiredMode.REQUIRED, example = "writing_rule")
    private String category;

    @Schema(description = "稳定编码")
    private String code;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "标题（门店名为店名）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "正文 Markdown；门店分类可留空，由结构化字段拼装")
    private String content;

    @Schema(description = "门店结构化详情（category=store 时使用）")
    private GeoReviewKbStoreAttrs storeAttrs;

    @Schema(description = "产品结构化详情（category=product 时使用）")
    private GeoReviewKbProductAttrs productAttrs;

    @Schema(description = "排序", example = "10")
    private Integer sortOrder;

    @Schema(description = "是否启用 1=是", example = "1")
    private Integer enabled;

    @Schema(description = "来源文件")
    private String sourceFile;

    @Schema(description = "备注")
    private String remark;
}
