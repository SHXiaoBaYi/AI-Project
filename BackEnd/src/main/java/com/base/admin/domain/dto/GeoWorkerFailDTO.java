package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "本机采集失败回报")
public class GeoWorkerFailDTO {

    @NotBlank(message = "采集电脑名称不能为空")
    @Size(max = 64)
    @Schema(description = "采集电脑名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String node;

    @NotNull(message = "队列ID不能为空")
    @Schema(description = "队列ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long jobId;

    @NotBlank(message = "失败类型不能为空")
    @Schema(description = "NEED_LOGIN=登录墙或验证码 / ERROR=其它失败", example = "NEED_LOGIN",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;

    @Size(max = 900)
    @Schema(description = "给人看的失败说明，不要包含密码")
    private String message;
}
