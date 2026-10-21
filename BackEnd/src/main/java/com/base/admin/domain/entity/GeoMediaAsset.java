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
@TableName("geo_media_asset")
@Schema(description = "GEO文章素材")
public class GeoMediaAsset extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "素材ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "文件路径")
    private String fileUrl;

    @Schema(description = "缩略图")
    private String thumbUrl;

    @Schema(description = "own|stock_licensed")
    private String sourceType;

    @Schema(description = "授权说明")
    private String licenseNote;

    @Schema(description = "图中是否已有品牌露出 0/1")
    private Integer brandVisible;

    @Schema(description = "默认图注")
    private String caption;

    @Schema(description = "无障碍描述")
    private String altText;

    @Schema(description = "标签JSON")
    private String tagsJson;

    @Schema(description = "可用于哪里/适用段落场景")
    private String usageHint;

    @Schema(description = "场景标签JSON")
    private String sceneTagsJson;

    @Schema(description = "宽")
    private Integer widthPx;

    @Schema(description = "高")
    private Integer heightPx;

    @Schema(description = "active|draft|disabled")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
