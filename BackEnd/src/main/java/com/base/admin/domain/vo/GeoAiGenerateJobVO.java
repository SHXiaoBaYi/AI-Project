package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO AI 生成文章异步任务进度")
public class GeoAiGenerateJobVO {

    @Schema(description = "任务ID")
    private String jobId;

    @Schema(description = "状态：RUNNING / SUCCESS / FAILED")
    private String status;

    @Schema(description = "当前阶段编码")
    private String currentCode;

    @Schema(description = "当前进度说明（质检/重写等细粒度文案）")
    private String currentMessage;

    @Schema(description = "流水线各阶段快照")
    private List<GeoPipelineStageVO> stages = new ArrayList<>();

    @Schema(description = "流式撰写中的标题草稿（打字机预览）")
    private String draftTitle;

    @Schema(description = "流式撰写中的 HTML 草稿（打字机预览，尚未落库）")
    private String draftHtml;

    @Schema(description = "成功后的生成结果")
    private GeoContentAiGenerateVO result;
}
