package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO素材查询")
public class GeoMediaAssetQueryDTO extends PageQuery {

    @Schema(description = "标题关键词")
    private String title;

    @Schema(description = "话题ID筛选")
    private Long topicId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "own|stock_licensed")
    private String sourceType;

    @Schema(description = "是否品牌露出")
    private Integer brandVisible;

    @Schema(description = "用途/标签关键词")
    private String keyword;
}
