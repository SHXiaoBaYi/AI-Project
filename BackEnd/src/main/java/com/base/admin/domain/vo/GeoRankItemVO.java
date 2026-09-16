package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "排行项")
public class GeoRankItemVO {

    @Schema(description = "名称", example = "搜狐")
    private String name;

    @Schema(description = "次数/数量", example = "12")
    private Integer value;

    @Schema(description = "附加说明")
    private String extra;
}
