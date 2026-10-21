package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量修改素材语义/用途")
public class GeoMediaAssetBatchMetaDTO {

    @NotEmpty
    @Schema(description = "素材ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @Schema(description = "可用于哪里（覆盖写）")
    private String usageHint;

    @Schema(description = "标签（覆盖写；null=不改）")
    private List<String> tags;

    @Schema(description = "场景标签（覆盖写；null=不改）")
    private List<String> sceneTags;

    @Schema(description = "关联话题（覆盖写；null=不改，空数组=清空）")
    private List<Long> topicIds;

    @Schema(description = "图中是否已有品牌；null=不改")
    private Integer brandVisible;

    @Schema(description = "状态；null=不改")
    private String status;

    @Schema(description = "来源；null=不改")
    private String sourceType;

    @Schema(description = "授权说明；null=不改")
    private String licenseNote;
}
