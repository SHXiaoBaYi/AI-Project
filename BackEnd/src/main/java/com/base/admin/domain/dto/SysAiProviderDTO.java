package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "更新 AI 厂商配置")
public class SysAiProviderDTO {

    @NotNull
    @Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "API Key；留空表示不修改已有 Key")
    private String apiKey;

    @NotBlank
    @Schema(description = "模型名", requiredMode = Schema.RequiredMode.REQUIRED, example = "qwen-turbo")
    private String model;

    @Schema(description = "兼容接口 baseUrl，空则用内置预设")
    private String baseUrl;

    @NotNull
    @Schema(description = "是否启用 1=启用 0=停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否清空 API Key", example = "false")
    private Boolean clearApiKey;
}
