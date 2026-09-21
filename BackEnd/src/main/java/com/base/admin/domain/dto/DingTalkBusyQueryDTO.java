package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "查询钉钉日程闲忙")
public class DingTalkBusyQueryDTO {

    @NotEmpty(message = "请选择用户")
    @Size(max = 20, message = "一次最多查询 20 个用户")
    @Schema(description = "系统用户ID，须已绑定钉钉", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> userIds;

    @NotNull(message = "请选择开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-21 09:00:00")
    private LocalDateTime startTime;

    @NotNull(message = "请选择结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-21 18:00:00")
    private LocalDateTime endTime;
}
