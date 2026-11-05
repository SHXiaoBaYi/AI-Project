package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "GEO自动监测-单题浏览器采集试跑入参")
public class GeoAutoProbeDTO {

    @NotBlank(message = "请选择监测平台")
    @Schema(description = "平台名（geo_platform.platform_name）", requiredMode = Schema.RequiredMode.REQUIRED, example = "豆包")
    private String platform;

    @NotBlank(message = "请输入监控问题")
    @Schema(description = "监控问题原文", requiredMode = Schema.RequiredMode.REQUIRED, example = "新疆适合寄内地的礼品有哪些？")
    private String question;

    @Schema(description = "是否开启深度思考", example = "false")
    private Boolean deepThink;

    @Schema(description = "是否生成整页截图存证", example = "true")
    private Boolean screenshot;

    @Schema(description = "提问位置", example = "全国随机")
    private String region;
}
