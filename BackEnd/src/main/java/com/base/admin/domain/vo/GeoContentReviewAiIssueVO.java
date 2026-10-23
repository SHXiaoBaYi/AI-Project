package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI预审划词建议")
public class GeoContentReviewAiIssueVO {

    @Schema(description = "选中原文")
    private String quoteText;

    @Schema(description = "批注说明")
    private String commentText;

    @Schema(description = "high/mid/low", example = "mid")
    private String level;

    @Schema(description = "选区前上下文", nullable = true)
    private String quotePrefix;

    @Schema(description = "选区后上下文", nullable = true)
    private String quoteSuffix;
}
