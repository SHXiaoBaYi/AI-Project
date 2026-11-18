package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "分配给某台采集电脑的平台账号（不含密码）")
public class GeoWorkerAccountVO {

    @Schema(description = "平台账号ID")
    private Long accountId;

    @Schema(description = "平台名")
    private String platform;

    @Schema(description = "账号脱敏")
    private String accountLabel;

    @Schema(description = "NONE/READY/FAILED/EXPIRED")
    private String loginStatus;

    @Schema(description = "提问页地址，供首次登录打开")
    private String url;
}
