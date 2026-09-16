package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "AI 厂商配置（Key 脱敏）")
public class SysAiProviderVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "厂商标识", example = "tongyi")
    private String provider;

    @Schema(description = "展示名", example = "通义千问")
    private String providerName;

    @Schema(description = "脱敏后的 API Key", example = "sk-****abcd")
    private String apiKeyMasked;

    @Schema(description = "是否已配置 Key")
    private boolean hasApiKey;

    @Schema(description = "模型名", example = "qwen-turbo")
    private String model;

    @Schema(description = "baseUrl")
    private String baseUrl;

    @Schema(description = "是否启用", example = "1")
    private Integer enabled;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
