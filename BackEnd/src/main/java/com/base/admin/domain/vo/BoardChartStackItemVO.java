package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图表下钻面包屑")
public class BoardChartStackItemVO {

    @Schema(description = "字段")
    private String field;

    @Schema(description = "key")
    private String key;

    @Schema(description = "展示名")
    private String label;
}
