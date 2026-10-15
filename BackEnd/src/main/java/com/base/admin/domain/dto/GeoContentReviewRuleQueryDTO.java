package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO文章审核规则分页查询")
public class GeoContentReviewRuleQueryDTO extends PageQuery {

    @Schema(description = "撰写人用户ID", example = "3", nullable = true)
    private Long writerUserId;

    @Schema(description = "是否跳过一审 1=是 0=否", example = "1", nullable = true)
    private Integer skipStage1;
}
