package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "考核任务延期")
public class HrKpiTaskDueUpdateDTO {

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "新的到期时间")
    private LocalDateTime dueAt;
}
