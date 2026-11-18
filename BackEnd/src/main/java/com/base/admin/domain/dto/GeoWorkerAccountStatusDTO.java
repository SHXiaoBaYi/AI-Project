package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "本机回报平台账号登录状态")
public class GeoWorkerAccountStatusDTO {

    @NotBlank(message = "采集电脑名称不能为空")
    @Size(max = 64)
    @Schema(description = "采集电脑名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String node;

    @NotNull(message = "账号ID不能为空")
    @Schema(description = "平台账号ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long accountId;

    @NotBlank(message = "登录状态不能为空")
    @Schema(description = "READY=本机窗口已登录 / EXPIRED=需要重新登录", example = "READY",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Size(max = 900)
    @Schema(description = "失败说明")
    private String error;
}
