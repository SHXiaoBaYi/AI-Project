package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO文章骨架查询")
public class GeoArticleSkeletonQueryDTO extends PageQuery {

    @Schema(description = "名称/编码关键词")
    private String keyword;

    @Schema(description = "是否启用")
    private Integer enabled;
}
