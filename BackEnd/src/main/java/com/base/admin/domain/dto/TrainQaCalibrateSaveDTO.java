package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "校准问答保存/人工校准")
public class TrainQaCalibrateSaveDTO {

    @Schema(description = "记录ID（新增不传）", example = "1", nullable = true)
    private Long id;

    @Schema(description = "文档ID", example = "1", nullable = true)
    private Long docId;

    @Schema(description = "版本ID", example = "4", nullable = true)
    private Long versionId;

    @Schema(description = "分类", example = "cashier")
    private String category;

    @NotBlank
    @Schema(description = "用户问法/意图原文（用于下次优先命中）", requiredMode = Schema.RequiredMode.REQUIRED, example = "如何寄存")
    private String userQuestion;

    @Schema(description = "同义问法，每行一条", example = "怎么寄存\n寄存怎么操作")
    private String aliases;

    @Schema(description = "文档话题标题", example = "问：前台寄存产品怎么操作？")
    private String topicTitle;

    @Schema(description = "答法纯文本")
    private String answer;

    @Schema(description = "答法 HTML")
    private String answerHtml;

    @Schema(description = "配图路径")
    private List<String> images = new ArrayList<>();

    @Schema(description = "状态 APPROVED=准 REJECTED=不准 PENDING=待审", example = "APPROVED")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
