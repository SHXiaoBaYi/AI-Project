package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "本机采集心跳")
public class GeoWorkerHeartbeatDTO {

    @NotBlank(message = "采集电脑名称不能为空")
    @Size(max = 64)
    @Schema(description = "采集电脑名称", example = "office-pc-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String node;

    @Size(max = 32)
    @Schema(description = "worker 版本")
    private String version;

    @Schema(description = "当前队列ID，空闲时不传")
    private Long currentJobId;

    @Schema(description = "当前平台账号ID，空闲时不传")
    private Long currentAccountId;
}
