package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO Excel 导入任务进度")
public class GeoImportJobVO {

    @Schema(description = "任务ID", example = "8f1c0b2e-1a2b-4c3d-9e8f-001122334455")
    private String jobId;

    @Schema(description = "状态：RUNNING / SUCCESS / FAILED", example = "RUNNING")
    private String status;

    @Schema(description = "总工作量", example = "120")
    private int total;

    @Schema(description = "已处理", example = "40")
    private int processed;

    @Schema(description = "进度 0-100", example = "33")
    private int percent;

    @Schema(description = "进度说明", example = "已处理 40 / 120")
    private String message;

    @Schema(description = "完成后的导入结果")
    private GeoImportResultVO result;
}
