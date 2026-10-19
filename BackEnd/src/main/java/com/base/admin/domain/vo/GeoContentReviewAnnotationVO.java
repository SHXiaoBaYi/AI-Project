package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "审核划词批注")
public class GeoContentReviewAnnotationVO {

    @Schema(description = "批注ID", example = "1")
    private Long id;

    @Schema(description = "审核轮次ID", example = "1")
    private Long reviewId;

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "选中原文")
    private String quoteText;

    @Schema(description = "选区前上下文")
    private String quotePrefix;

    @Schema(description = "选区后上下文")
    private String quoteSuffix;

    @Schema(description = "纯文本起点偏移", example = "120")
    private Integer startOffset;

    @Schema(description = "纯文本终点偏移", example = "150")
    private Integer endOffset;

    @Schema(description = "批注内容")
    private String commentText;

    @Schema(description = "批注人用户ID", example = "1")
    private Long authorUserId;

    @Schema(description = "批注人展示名", example = "李金瑜")
    private String authorName;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
