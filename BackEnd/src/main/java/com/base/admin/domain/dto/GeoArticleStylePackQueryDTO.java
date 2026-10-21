package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO风格包查询")
public class GeoArticleStylePackQueryDTO extends PageQuery {

    @Schema(description = "名称/编码关键词")
    private String keyword;

    @Schema(description = "平台提示")
    private String platformHint;

    @Schema(description = "是否启用")
    private Integer enabled;
}
