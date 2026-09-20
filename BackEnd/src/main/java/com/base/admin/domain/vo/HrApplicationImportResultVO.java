package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "候选人导入结果。有任何错误时整次不写入")
public class HrApplicationImportResultVO {

    @Schema(description = "是否全部成功")
    private boolean success;

    @Schema(description = "数据行数，不含表头和空行")
    private int total;

    @Schema(description = "实际写入条数。失败时为 0")
    private int successCount;

    @Schema(description = "错误明细")
    private List<HrApplicationImportErrorVO> errors = new ArrayList<>();
}
