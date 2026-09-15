package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO日监测整单批量保存结果")
public class GeoDailyBulkSaveResultVO {

    @Schema(description = "是否需要确认忽略已统计冲突（true 表示未落库）", example = "true")
    private boolean needConfirm;

    @Schema(description = "新增条数", example = "8")
    private int insertCount;

    @Schema(description = "覆盖更新条数", example = "3")
    private int updateCount;

    @Schema(description = "因已统计而跳过的条数", example = "2")
    private int skippedLockedCount;

    @Schema(description = "已统计冲突列表（needConfirm=true 时返回）")
    private List<GeoDailyBulkConflictVO> lockedConflicts = new ArrayList<>();
}
