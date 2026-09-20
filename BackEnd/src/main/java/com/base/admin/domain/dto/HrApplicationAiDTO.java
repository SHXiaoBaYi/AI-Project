package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "生成候选人 AI 分析")
public class HrApplicationAiDTO {

    @NotNull
    @Schema(description = "投递ID")
    private Long applicationId;

    @NotBlank
    @Schema(description = "厂商标识，如 tongyi、deepseek")
    private String provider;
}
