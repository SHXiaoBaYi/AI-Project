package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 外网一次提交的监控批次。同一 runId 再次提交会覆盖该批次。 */
@Data
@Schema(description = "外网提交监控批次")
public class GeoAutoExternalResultSaveDTO {

    @NotNull(message = "taskId 不能为空")
    @Schema(description = "监测任务ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long taskId;

    @NotBlank(message = "runId 不能为空")
    @Size(max = 64, message = "runId 最长 64 位")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "runId 只能包含字母、数字、下划线和中划线")
    @Schema(description = "外网批次号，重复提交会覆盖同一批", requiredMode = Schema.RequiredMode.REQUIRED, example = "1_20261117_110500")
    private String runId;

    @Size(max = 128, message = "taskName 最长 128 字")
    @Schema(description = "任务名快照", example = "测试")
    private String taskName;

    @Schema(description = "本轮开始时间", example = "2026-11-17 11:05:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "本轮结束时间", example = "2026-11-17 11:09:30")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "SUCCESS/PARTIAL/FAILED/RUNNING", example = "PARTIAL")
    private String status;

    @Size(max = 32, message = "source 最长 32 字")
    @Schema(description = "提交来源", example = "browser-skill")
    private String source;

    @NotEmpty(message = "results 不能为空")
    @Size(max = 200, message = "单次最多提交 200 条结果")
    @Valid
    @Schema(description = "本轮各问题各平台的采集结果", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<GeoAutoExternalResultItemDTO> results;
}
