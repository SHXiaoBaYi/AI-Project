package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "文章发布/收录看板明细查询")
public class GeoContentArticleDetailQueryDTO extends GeoContentArticleBoardQueryDTO {

    @NotBlank
    @Schema(description = "明细类型：publish / cite / platformRank / articleRank", example = "publish")
    private String detailType;

    @Schema(description = "下钻维度键（日期/发布人/平台/文章标题等）")
    private String dimensionKey;
}
