package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "为问题增加一个内容发布平台上的文章")
public class GeoContentPlatformArticleDTO {

    @NotNull
    @Schema(description = "投放主表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long placementId;

    @NotBlank
    @Schema(description = "内容发布平台", requiredMode = Schema.RequiredMode.REQUIRED, example = "搜狐")
    private String platformName;

    @Schema(description = "内容形态（图文/视频）", example = "图文")
    private String contentForm;
}
