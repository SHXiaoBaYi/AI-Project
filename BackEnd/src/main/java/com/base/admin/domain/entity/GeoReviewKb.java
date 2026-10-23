package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import com.base.admin.domain.dto.GeoReviewKbProductAttrs;
import com.base.admin.domain.dto.GeoReviewKbStoreAttrs;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_review_kb")
@Schema(description = "GEO内容审核知识库条目")
public class GeoReviewKb extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "分类 writing_rule/ai_friendly/article_structure/brand/store/product/faq", example = "writing_rule")
    private String category;

    @Schema(description = "稳定编码，种子幂等", example = "writing-notes")
    private String code;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文 Markdown；门店可由 attrs 拼装")
    private String content;

    @Schema(description = "分类扩展 JSON：门店营业信息 / 产品系列与SKU")
    private String attrs;

    @TableField(exist = false)
    @Schema(description = "门店结构化字段（由 attrs 解析，仅 store 分类）")
    private GeoReviewKbStoreAttrs storeAttrs;

    @TableField(exist = false)
    @Schema(description = "产品结构化字段（由 attrs 解析，仅 product 分类）")
    private GeoReviewKbProductAttrs productAttrs;

    @Schema(description = "排序，越小越靠前", example = "10")
    private Integer sortOrder;

    @Schema(description = "是否启用 1=是", example = "1")
    private Integer enabled;

    @Schema(description = "来源文件备注")
    private String sourceFile;

    @Schema(description = "备注")
    private String remark;
}
