package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO文章发布人规则分页查询")
public class GeoContentPublisherRuleQueryDTO extends PageQuery {

    @Schema(description = "发布人用户ID", example = "3")
    private Long publisherUserId;

    @Schema(description = "是否默认 1/0", example = "1")
    private Integer isDefault;
}
