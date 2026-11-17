package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 外网提交监控数据的回执。不含截图内容。 */
@Data
@Schema(description = "外网提交监控数据回执")
public class GeoAutoExternalSaveVO {

    @Schema(description = "系统内批次ID", example = "12")
    private Long id;

    @Schema(description = "外网批次号", example = "1_20261117_110500")
    private String runId;

    @Schema(description = "本轮结果条数", example = "2")
    private Integer totalCount;

    @Schema(description = "成功条数", example = "2")
    private Integer successCount;

    @Schema(description = "失败条数", example = "0")
    private Integer failedCount;

    @Schema(description = "true=覆盖了同一 runId 的上一轮提交")
    private Boolean replaced;
}
