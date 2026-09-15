package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO看板落库结果")
public class GeoPersistResultVO {

    @Schema(description = "写入/更新的快照行数", example = "12")
    private int snapshotCount;

    @Schema(description = "锁定的日监测行数", example = "48")
    private int lockedDailyCount;

    @Schema(description = "涉及的周期数", example = "5")
    private int periodCount;
}
