package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "图表下钻路径节点")
public class BoardChartStackItemDTO {

    @Schema(description = "层级字段 topic|person|platform|stage|theme", example = "topic")
    private String field;

    @Schema(description = "维度值", example = "新疆羊肉")
    private String key;

    @Schema(description = "展示名", example = "新疆羊肉")
    private String label;
}
