package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("geo_sell_authority")
@Schema(description = "卖点-专业背书与权威认证明细")
public class GeoSellAuthority {

    @TableId
    @Schema(description = "卖点条目ID")
    private Long pointId;

    @Schema(description = "背书渠道")
    private String channel;

    @Schema(description = "准入标准")
    private String admission;

    @Schema(description = "遴选流程")
    private String processText;

    @Schema(description = "背书意义")
    private String meaning;

    @Schema(description = "补充说明或总述正文")
    private String extraNote;
}
