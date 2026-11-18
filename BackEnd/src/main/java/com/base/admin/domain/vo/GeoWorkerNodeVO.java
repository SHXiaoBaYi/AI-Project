package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "一台采集电脑的在线状态")
public class GeoWorkerNodeVO {

    @Schema(description = "采集电脑名称")
    private String nodeName;

    @Schema(description = "最近心跳")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSeen;

    @Schema(description = "心跳未超时即为在线")
    private boolean online;

    @Schema(description = "当前队列ID")
    private Long currentJobId;

    @Schema(description = "当前平台账号ID")
    private Long currentAccountId;

    @Schema(description = "worker 版本")
    private String version;
}
