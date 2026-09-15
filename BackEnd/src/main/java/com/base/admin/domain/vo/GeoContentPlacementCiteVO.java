package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内容投放-AI引用明细")
public class GeoContentPlacementCiteVO {

    @Schema(description = "引用ID", example = "1")
    private Long id;

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "关联平台投放明细ID", example = "1", nullable = true)
    private Long itemId;

    @Schema(description = "提问问题")
    private String askQuestion;

    @Schema(description = "AI平台名称", example = "豆包")
    private String aiPlatform;

    @Schema(description = "引用链接")
    private String citeUrl;

    @Schema(description = "备注")
    private String remark;
}
