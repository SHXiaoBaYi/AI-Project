package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "电商导入冲突行")
public class EcomImportConflictVO {

    @Schema(description = "业务日或点击日")
    private String bizDate;

    @Schema(description = "维度说明，如渠道/SPU/计划")
    private String dimension;

    @Schema(description = "原因")
    private String reason;
}
