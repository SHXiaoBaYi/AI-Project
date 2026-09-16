package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "可用 AI 厂商选项")
public class GeoAiProviderOptionVO {

    @Schema(description = "厂商标识", example = "tongyi")
    private String provider;

    @Schema(description = "展示名", example = "通义千问")
    private String label;

    @Schema(description = "默认模型", example = "qwen-turbo")
    private String model;

    @Schema(description = "是否已配置可用")
    private boolean available;

    @Schema(description = "提示", example = "qwen-turbo")
    private String hint;
}
