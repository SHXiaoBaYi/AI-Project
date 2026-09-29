package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "电商导入结果")
public class EcomImportResultVO {

    @Schema(description = "是否需要确认（存在已归档锁行）")
    private boolean needConfirm;

    @Schema(description = "导入批次ID")
    private Long batchId;

    @Schema(description = "平台")
    private String platform;

    @Schema(description = "店铺ID")
    private Long shopId;

    @Schema(description = "店铺编码")
    private String shopCode;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "报表类型")
    private String reportType;

    @Schema(description = "报表名称")
    private String reportLabel;

    @Schema(description = "总行数")
    private int totalRows;

    @Schema(description = "成功写入行数")
    private int successRows;

    @Schema(description = "跳过的已归档锁行数")
    private int skippedLockedRows;

    @Schema(description = "失败行数")
    private int failRows;

    @Schema(description = "强制覆盖的锁行数")
    private int forcedRows;

    @Schema(description = "结果说明")
    private String message;

    @Schema(description = "已归档冲突行（needConfirm 或 skip 后回显）")
    private List<EcomImportConflictVO> lockedConflicts = new ArrayList<>();
}
