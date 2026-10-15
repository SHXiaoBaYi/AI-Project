package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("geo_sell_quality")
@Schema(description = "卖点-品控明细（模块/小节/项目/标准）")
public class GeoSellQuality {

    @TableId
    @Schema(description = "卖点条目ID")
    private Long pointId;

    @Schema(description = "模块", example = "全链路高标准品控")
    private String moduleName;

    @Schema(description = "小节", example = "1.1 资质与标准背书")
    private String sectionName;

    @Schema(description = "项目", example = "十年有机双认证")
    private String itemName;

    @Schema(description = "内容/标准/要求")
    private String standardText;
}
