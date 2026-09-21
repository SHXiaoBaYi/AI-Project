package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "确认落库的相似问题")
public class GeoSimilarQuestionCommitDTO {

    @Valid
    @NotEmpty(message = "请选择要落库的相似问题")
    @Schema(description = "勾选的问题")
    private List<Item> items;

    @Data
    @Schema(description = "一条待落库的相似问题")
    public static class Item {

        @NotNull(message = "缺少源投放")
        @Schema(description = "源投放ID")
        private Long sourcePlacementId;

        @NotBlank(message = "相似问题不能为空")
        @Schema(description = "相似问题")
        private String targetQuestion;

        @Schema(description = "生成模型")
        private String sourceAiModel;
    }
}
