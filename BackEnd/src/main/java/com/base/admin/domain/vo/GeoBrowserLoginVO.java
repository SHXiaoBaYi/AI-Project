package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 平台账号模拟登录结果 */
@Data
@Schema(description = "浏览器账号模拟登录结果")
public class GeoBrowserLoginVO {

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "失败原因（成功为空）")
    private String error;

    @Schema(description = "平台名")
    private String platform;

    @Schema(description = "使用的账号（脱敏）")
    private String accountLabel;

    @Schema(description = "登录态来源：CACHED/PASSWORD_LOGIN/STORAGE_STATE/MANUAL_UPLOAD")
    private String source;

    @Schema(description = "登录态文件")
    private String statePath;

    @Schema(description = "失败时的页面截图")
    private String screenshotUrl;

    @Schema(description = "诊断 JSON（含每一步选择器命中情况，绝不含密码）")
    private String diagnostic;
}
