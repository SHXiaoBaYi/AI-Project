package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "话题下目标问题下拉项")
public class GeoTargetQuestionOptionVO {

    @Schema(description = "内容投放ID", example = "10")
    private Long placementId;

    @Schema(description = "目标问题")
    private String targetQuestion;
}
