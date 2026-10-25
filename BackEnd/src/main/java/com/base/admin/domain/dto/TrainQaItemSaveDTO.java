package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "修改培训问答题目")
public class TrainQaItemSaveDTO {

    @NotBlank
    @Schema(description = "题干", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @Schema(description = "同义问法，一行一条")
    private String aliases;

    @Schema(description = "答文。留空则不改原答案")
    private String answer;

    @Schema(description = "答文富文本。传入后按这份 HTML 保存，并保留其中的配图")
    private String answerHtml;
}
