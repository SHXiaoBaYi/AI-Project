package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "统一看板-任务下钻")
public class BoardTaskDrillQueryDTO extends PageQuery {

    @Schema(description = "指标：total/pending/doing/done/cancelled/overdue/priority/owner/status")
    private String metric;

    @Schema(description = "维度键，如状态值、优先级、负责人ID")
    private String dimKey;

    @Schema(description = "开始日期")
    private String startDate;

    @Schema(description = "结束日期")
    private String endDate;

    @Schema(description = "负责人")
    private Long ownerUserId;

    @Schema(description = "执行人")
    private Long assigneeUserId;

    @Schema(description = "任务类型")
    private String taskType;
}
