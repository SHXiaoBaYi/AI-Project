package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "审稿知识库-产品结构化字段（系列或SKU）")
public class GeoReviewKbProductAttrs {

    @Schema(description = "条目类型：series=系列口径 / sku=具体产品", example = "sku")
    private String kind;

    @Schema(description = "系列编码", example = "rugao")
    private String seriesCode;

    @Schema(description = "系列名称", example = "乳羔羊系列")
    private String seriesName;

    @Schema(description = "净重", example = "5kg")
    private String netWeight;

    @Schema(description = "毛重", example = "6.5kg")
    private String grossWeight;

    @Schema(description = "规格（斤/盒等）", example = "10斤")
    private String specJin;

    @Schema(description = "参考价（元或元/kg，以最新线上为准）", example = "999")
    private String priceYuan;

    @Schema(description = "所含部位说明")
    private String parts;

    @Schema(description = "产品主图相对路径 /uploads/...", example = "/uploads/geo/handbook/rugao-quan-yang.jpg")
    private String imageUrl;

    @Schema(description = "精品产品手册页码（扫描版）", example = "3")
    private Integer handbookPage;
}
