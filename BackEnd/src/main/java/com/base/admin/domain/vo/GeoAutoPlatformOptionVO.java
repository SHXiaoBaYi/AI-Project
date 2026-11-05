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

    @Schema(description = "采集通道编码（浏览器通道固定 BROWSER，未就绪为空）", example = "BROWSER")
    private String providerCode;

    @Schema(description = "网页版采集适配器是否就绪", example = "true")
    private boolean providerReady;

    @Schema(description = "未就绪提示（未配置适配器 / 未配置登录态等）")
    private String hint;
}
