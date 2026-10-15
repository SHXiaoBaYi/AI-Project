package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("geo_sell_social")
@Schema(description = "卖点-社会责任/品牌明细")
public class GeoSellSocial {

    @TableId
    @Schema(description = "卖点条目ID")
    private Long pointId;

    @Schema(description = "维度", example = "社会责任感")
    private String dimension;

    @Schema(description = "卖点/事件名")
    private String eventName;

    @Schema(description = "行动内容")
    private String actionContent;

    @Schema(description = "备注")
    private String eventRemark;
}
