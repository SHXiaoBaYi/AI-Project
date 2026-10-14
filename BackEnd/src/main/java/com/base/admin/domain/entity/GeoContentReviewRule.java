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
@TableName("geo_content_review_rule")
@Schema(description = "GEO文章审核规则")
public class GeoContentReviewRule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "规则ID", example = "1")
    private Long id;

    @Schema(description = "撰写人用户ID", example = "3")
    private Long writerUserId;

    @Schema(description = "撰写人展示名", example = "张三")
    private String writerName;

    @Schema(description = "是否跳过一审 1=是 0=否", example = "1")
    private Integer skipStage1;

    @Schema(description = "备注")
    private String remark;
}
