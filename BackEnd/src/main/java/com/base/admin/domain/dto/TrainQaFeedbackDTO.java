package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "答疑结果人工标记（准/不准）")
public class TrainQaFeedbackDTO {

    @NotBlank
    @Schema(description = "用户原问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "如何寄存")
    private String userQuestion;

    @NotNull
    @Schema(description = "是否准确（true=准 false=不准）", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean accurate;

    @Schema(description = "分类", example = "cashier")
    private String category;

    @Schema(description = "文档ID（不传则取最新收银文档）", example = "1", nullable = true)
    private Long docId;

    @Schema(description = "版本ID（不传则取文档最新版）", example = "4", nullable = true)
    private Long versionId;

    @Schema(description = "已有校准记录ID（再次标记时传）", example = "1", nullable = true)
    private Long calibrateId;

    @Schema(description = "文档话题/问法标题", example = "问：前台寄存产品怎么操作？")
    private String topicTitle;

    @Schema(description = "答法纯文本")
    private String answer;

    @Schema(description = "答法 HTML（图文混排）")
    private String answerHtml;

    @Schema(description = "配图路径列表")
    private List<String> images = new ArrayList<>();
}
