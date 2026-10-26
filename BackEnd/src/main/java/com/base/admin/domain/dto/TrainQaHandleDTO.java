package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "答疑校准/未命中提问标记处理")
public class TrainQaHandleDTO {

    @NotNull
    @Schema(description = "记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @NotBlank
    @Schema(
            description = "处理状态 OPEN=未处理 INTENT_BAD=用户意图不准 IGNORE=忽略这样的问题 DOC_MISSING=培训文档缺失",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "INTENT_BAD")
    private String handleStatus;
}
