package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "员工AI收录对比行")
public class GeoContentPublisherCiteRowVO {

    @Schema(description = "发布人用户ID")
    private Long publisherUserId;

    @Schema(description = "发布人")
    private String publisherName;

    @Schema(description = "成功发布数")
    private Integer successCount;

    @Schema(description = "被收录数")
    private Integer citedCount;

    @Schema(description = "引用次数（cite 行数）")
    private Integer citeHitCount;

    @Schema(description = "收录率%")
    private Double citeRate;

    @Schema(description = "收录率环比（百分点）")
    private Double citeRateMom;

    @Schema(description = "收录率同比（百分点）")
    private Double citeRateYoy;

    @Schema(description = "引用次数环比")
    private Integer citeHitCountMom;

    @Schema(description = "引用次数同比")
    private Integer citeHitCountYoy;
}
