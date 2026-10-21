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
@TableName("geo_article_style_pack")
@Schema(description = "GEO文章风格包")
public class GeoArticleStylePack extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "编码", example = "zhihu_explain")
    private String code;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "适用平台提示")
    private String platformHint;

    @Schema(description = "语气与节奏")
    private String toneGuide;

    @Schema(description = "结构说明")
    private String structureGuide;

    @Schema(description = "可摘句式 JSON")
    private String samplePhrases;

    @Schema(description = "品牌露出软约束")
    private String brandGuide;

    @Schema(description = "1启用 0停用")
    private Integer enabled;

    @Schema(description = "排序")
    private Integer sortOrder;
}
