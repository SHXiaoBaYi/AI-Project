package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "候选人导入的一行错误")
public class HrApplicationImportErrorVO {

    @Schema(description = "Excel 行号，从表头为第 1 行起算")
    private int rowIndex;

    @Schema(description = "错误说明")
    private String message;
}
