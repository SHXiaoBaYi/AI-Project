package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 服务器登录窗口快照。画面是 JPEG 的 Base64，不含 Cookie。 */
@Data
@Schema(description = "服务器登录窗口")
public class GeoBrowserLoginBoothVO {

    @Schema(description = "平台账号ID", example = "4")
    private Long accountId;

    @Schema(description = "平台名", example = "豆包")
    private String platform;

    @Schema(description = "OPEN / SAVED / CLOSED", example = "OPEN")
    private String phase;

    @Schema(description = "当前画面是否已看不到登录按钮")
    private boolean loginDetected;

    @Schema(description = "给操作者的提示")
    private String message;

    @Schema(description = "画面 JPEG 的 Base64，轮询时返回")
    private String imageBase64;

    @Schema(description = "视口宽度", example = "1280")
    private Integer viewportWidth;

    @Schema(description = "视口高度", example = "800")
    private Integer viewportHeight;
}
