package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_content_review_annotation")
@Schema(description = "GEO 文章审核划词/划句/划段批注")
public class GeoContentReviewAnnotation extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "审核轮次ID", example = "1")
    private Long reviewId;

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "选中原文", example = "第二段数据和话题不符")
    private String quoteText;

    @Schema(description = "选区前上下文（消歧）")
    private String quotePrefix;

    @Schema(description = "选区后上下文（消歧）")
    private String quoteSuffix;

    @Schema(description = "纯文本起点偏移（可选）", example = "120")
    private Integer startOffset;

    @Schema(description = "纯文本终点偏移（可选）", example = "150")
    private Integer endOffset;

    @Schema(description = "批注内容")
    private String commentText;

    @Schema(description = "批注人用户ID", example = "1")
    private Long authorUserId;

    @Schema(description = "批注人展示名", example = "李金瑜")
    private String authorName;
}
