package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO自动监测-可监测平台选项")
public class GeoAutoPlatformOptionVO {

    @Schema(description = "平台ID", example = "1")
    private Long id;

    @Schema(description = "平台名称", example = "豆包")
    private String platformName;

    @Schema(description = "AI厂商编码（doubao/deepseek/moonshot/tongyi/zhipu）", example = "doubao")
    private String providerCode;

    @Schema(description = "厂商是否已配置可调用", example = "true")
    private boolean providerReady;

    @Schema(description = "未就绪提示（厂商未配置 API Key 等）")
    private String hint;
}
