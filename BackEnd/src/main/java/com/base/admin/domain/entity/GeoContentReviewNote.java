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
@TableName("geo_content_review_note")
@Schema(description = "内容投放平台文章内部审核意见")
public class GeoContentReviewNote extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "平台文章ID", example = "1")
    private Long itemId;

    @Schema(description = "内部意见")
    private String opinion;

    @Schema(description = "意见人展示名", example = "李金瑜")
    private String authorName;
}
