package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "流水线生成结果（未落库）")
public class GeoArticlePipelineResultVO {

    private String title;
    private String html;
    private String sourceAiModel;
    private Integer usedPointCount;
    private Integer usedImageCount;
    private Integer qaRounds;
    private String styleCode;
    private String skeletonCode;
    private List<Long> usedAssetIds = new ArrayList<>();
    private List<GeoPipelineStageVO> stages = new ArrayList<>();
}
