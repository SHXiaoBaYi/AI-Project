package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "半小时一段的闲忙状态；查询范围首尾不足半小时时按实际起止")
public class DingTalkBusySlotVO {

    @Schema(description = "状态：FREE 闲 / BUSY 忙 / TENTATIVE 暂定", example = "BUSY")
    private String status;

    @Schema(description = "状态中文", example = "忙")
    private String statusLabel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间", example = "2026-09-21 09:00:00")
    private LocalDateTime start;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间", example = "2026-09-21 10:00:00")
    private LocalDateTime end;
}
