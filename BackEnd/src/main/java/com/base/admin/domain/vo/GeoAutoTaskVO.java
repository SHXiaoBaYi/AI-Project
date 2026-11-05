package com.base.admin.domain.vo;

import com.base.admin.domain.entity.GeoMonitorTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO自动监测-任务（含统计）")
public class GeoAutoTaskVO extends GeoMonitorTask {

    @Schema(description = "监测平台名列表", example = "[\"豆包\",\"DS\"]")
    private List<String> platformList;

    @Schema(description = "圈选ID列表", example = "[1,2,3]")
    private List<Long> scopeIdList;

    @Schema(description = "圈选问题数（运行时实际展开）", example = "46")
    private Integer questionCount;

    @Schema(description = "预估每日调用次数（问题数×平台数×频率系数）", example = "184")
    private Integer estimatedDailyCalls;

    @Schema(description = "最近一次运行状态：RUNNING/SUCCESS/PARTIAL/FAILED", example = "SUCCESS")
    private String lastRunStatus;

    @Schema(description = "最近一次运行开始时间")
    private String lastRunStartTime;

    @Schema(description = "最近一次运行 done/total", example = "40/40")
    private String lastRunProgress;
}
