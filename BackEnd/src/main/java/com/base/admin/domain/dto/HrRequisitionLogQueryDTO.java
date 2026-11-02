package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "招聘需求操作日志查询")
public class HrRequisitionLogQueryDTO extends PageQuery {

    @Schema(description = "需求ID")
    private Long requisitionId;

    @Schema(description = "动作 CREATE/UPDATE/DELETE/STATUS/TASK")
    private String action;

    @Schema(description = "入口 REQUISITION/PANEL")
    private String entrySource;

    @Schema(description = "岗位名或操作人关键词")
    private String keyword;
}
