package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "电商异步导入任务")
public class EcomImportJobVO {

    @Schema(description = "任务ID")
    private String jobId;

    @Schema(description = "RUNNING/SUCCESS/FAILED")
    private String status;

    @Schema(description = "总行数")
    private int total;

    @Schema(description = "已处理行数")
    private int processed;

    @Schema(description = "进度百分比")
    private int percent;

    @Schema(description = "提示")
    private String message;

    @Schema(description = "完成后的结果")
    private EcomImportResultVO result;
}
