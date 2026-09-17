package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "任务看板汇总查询（③④受日期范围约束）")
public class BoardTaskOpsQueryDTO {

    @Schema(description = "开始日期 yyyy-MM-dd，作用于按时完成率/完成率")
    private String startDate;

    @Schema(description = "结束日期 yyyy-MM-dd，作用于按时完成率/完成率")
    private String endDate;

    @Schema(description = "锚定「今日/本周」的业务日，默认服务器当天；演示可传 2015-12-31")
    private String asOfDate;
}
