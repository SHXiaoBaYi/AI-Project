package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "相似问题预览，尚未落库")
public class GeoSimilarQuestionPreviewVO {

    @Schema(description = "源投放ID")
    private Long sourcePlacementId;

    @Schema(description = "源目标问题")
    private String sourceQuestion;

    @Schema(description = "生成的相似问题")
    private String targetQuestion;

    @Schema(description = "生成模型")
    private String sourceAiModel;

    @Schema(description = "同话题下是否已有相同问题")
    private Boolean duplicated;
}
