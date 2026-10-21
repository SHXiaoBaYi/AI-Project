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
@TableName("geo_article_skeleton")
@Schema(description = "GEO文章骨架")
public class GeoArticleSkeleton extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "编码", example = "qa_sections_v1")
    private String code;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "章节骨架 JSON")
    private String sectionSchema;

    @Schema(description = "1启用 0停用")
    private Integer enabled;

    @Schema(description = "排序")
    private Integer sortOrder;
}
