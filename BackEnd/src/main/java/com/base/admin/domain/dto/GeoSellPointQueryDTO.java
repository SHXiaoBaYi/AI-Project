package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "卖点条目分页查询")
public class GeoSellPointQueryDTO extends PageQuery {

    @Schema(description = "域ID（含子域）", example = "1")
    private Long domainId;

    @Schema(description = "标题关键词")
    private String title;

    @Schema(description = "状态 active/draft/disabled")
    private String status;

    @Schema(description = "证据级别 soft/hard")
    private String evidenceLevel;

    @Schema(description = "全文关键词（标题/主张/论据/标签）")
    private String keyword;

    @Schema(description = "来源 Excel sheet 名（模糊）")
    private String sourceSheet;
}
