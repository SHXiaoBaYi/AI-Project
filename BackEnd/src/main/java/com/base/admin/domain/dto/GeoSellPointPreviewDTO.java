package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "卖点召回预览请求")
public class GeoSellPointPreviewDTO {

    @Schema(description = "话题名称")
    private String topicName;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "最多返回条数", example = "12")
    private Integer limit;
}
