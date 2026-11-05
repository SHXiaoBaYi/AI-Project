package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 平台账号模拟登录 / 刷新登录态 */
@Data
@Schema(description = "浏览器账号模拟登录")
public class GeoAutoBrowserLoginDTO {

    @NotBlank(message = "平台不能为空")
    @Schema(description = "平台名（须与 geo_platform.platform_name 一致）", requiredMode = Schema.RequiredMode.REQUIRED, example = "豆包")
    private String platform;

    @Schema(description = "是否强制重新登录（忽略未过期的登录态缓存）", example = "false")
    private Boolean forceRefresh;

    @Schema(description = "按账号ID指定账号；为空则取该平台第一个启用的浏览器账号", example = "1")
    private Long accountId;
}
