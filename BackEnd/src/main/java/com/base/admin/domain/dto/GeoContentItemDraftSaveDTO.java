package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "保存某一内容平台上的文章稿（统一稿或独立稿）")
public class GeoContentItemDraftSaveDTO {

    @NotNull
    @Schema(description = "平台文章ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long itemId;

    @NotBlank
    @Schema(description = "正文来源 shared=沿用统一稿 custom=独立稿", requiredMode = Schema.RequiredMode.REQUIRED, example = "shared")
    private String contentMode;

    @Schema(description = "独立稿标题，统一稿模式可空")
    private String contentTitle;

    @Schema(description = "独立稿 Lake 正文，统一稿模式可空")
    private String contentBody;

    @Schema(description = "独立稿乐观锁版本", example = "0", nullable = true)
    private Long contentVersion;
}
