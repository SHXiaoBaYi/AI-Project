package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO内容审核知识库查询")
public class GeoReviewKbQueryDTO extends PageQuery {

    @Schema(description = "分类")
    private String category;

    @Schema(description = "标题关键词")
    private String title;

    @Schema(description = "是否启用 1/0")
    private Integer enabled;
}
