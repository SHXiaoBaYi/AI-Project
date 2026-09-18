package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "任务看板汇总查询（筛选作用于全部指标）")
public class BoardTaskOpsQueryDTO {

    @Schema(description = "开始日期 yyyy-MM-dd")
    private String startDate;

    @Schema(description = "结束日期 yyyy-MM-dd")
    private String endDate;

    @Schema(description = "粒度 day/week/month/year，用于默认区间与文案")
    private String grain;

    @Schema(description = "人员筛选：执行人或负责人用户ID，多选；空表示不限")
    private List<Long> filterUserIds;

    @Schema(description = "任务类型，多选；空表示不限")
    private List<String> taskTypes;
}
