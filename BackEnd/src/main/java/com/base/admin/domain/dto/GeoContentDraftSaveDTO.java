package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "GEO内容投放在线正文保存（text/lake）")
public class GeoContentDraftSaveDTO {

    @NotNull
    @Schema(description = "投放主表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long placementId;

    @Schema(description = "统一稿标题，传空字符串表示清空", example = "中秋送礼指南")
    private String contentTitle;

    @Schema(description = "Lake 正文（text/lake）", example = "<!doctype lake>...")
    private String contentBody;

    @Schema(description = "乐观锁版本号（首次可为 null）", example = "1", nullable = true)
    private Long contentVersion;
}
