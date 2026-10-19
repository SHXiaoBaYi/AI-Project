package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "新增审核划词批注")
public class GeoContentReviewAnnotationSaveDTO {

    @NotNull
    @Schema(description = "审核轮次ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long reviewId;

    @NotBlank
    @Schema(description = "选中原文", requiredMode = Schema.RequiredMode.REQUIRED, example = "第二段数据和话题不符")
    private String quoteText;

    @Schema(description = "选区前上下文", example = "……")
    private String quotePrefix;

    @Schema(description = "选区后上下文", example = "……")
    private String quoteSuffix;

    @Schema(description = "纯文本起点偏移", example = "120")
    private Integer startOffset;

    @Schema(description = "纯文本终点偏移", example = "150")
    private Integer endOffset;

    @NotBlank
    @Schema(description = "批注内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "请改成与目标问题一致的数据")
    private String commentText;
}
