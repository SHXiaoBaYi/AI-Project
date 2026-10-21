package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "GEO素材保存")
public class GeoMediaAssetDTO {

    @Schema(description = "素材ID，更新时必填")
    private Long id;

    @NotBlank
    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank
    @Schema(description = "图片路径", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileUrl;

    @Schema(description = "缩略图")
    private String thumbUrl;

    @Schema(description = "own|stock_licensed", example = "own")
    private String sourceType;

    @Schema(description = "授权说明（库存图必填授权信息）")
    private String licenseNote;

    @Schema(description = "图中是否已有品牌露出")
    private Integer brandVisible;

    @Schema(description = "图注")
    private String caption;

    @Schema(description = "alt")
    private String altText;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "可用于哪里/适用段落，如：开篇氛围、认证背书、牧场实景")
    private String usageHint;

    @Schema(description = "场景标签")
    private List<String> sceneTags;

    @Schema(description = "宽")
    private Integer widthPx;

    @Schema(description = "高")
    private Integer heightPx;

    @Schema(description = "active|draft|disabled")
    private String status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "关联话题（可选，软优先）")
    private List<Long> topicIds;
}
