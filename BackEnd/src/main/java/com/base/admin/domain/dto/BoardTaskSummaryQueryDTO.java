package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "统一看板-任务汇总查询")
public class BoardTaskSummaryQueryDTO {

    @Schema(description = "开始日期 yyyy-MM-dd")
    private String startDate;

    @Schema(description = "结束日期 yyyy-MM-dd")
    private String endDate;

    @Schema(description = "负责人用户ID")
    private Long ownerUserId;

    @Schema(description = "执行人用户ID")
    private Long assigneeUserId;

    @Schema(description = "任务类型")
    private String taskType;
}
