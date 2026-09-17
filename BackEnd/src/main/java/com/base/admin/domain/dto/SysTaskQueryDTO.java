package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务分页查询")
public class SysTaskQueryDTO extends PageQuery {

    @Schema(description = "标题（模糊）")
    private String title;

    @Schema(description = "任务类型")
    private String taskType;

    @Schema(description = "状态（单选，兼容）")
    private String status;

    @Schema(description = "状态（多选）")
    private List<String> statuses;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "负责人用户ID")
    private Long ownerUserId;

    @Schema(description = "执行人用户ID")
    private Long assigneeUserId;

    @Schema(description = "创建人用户ID")
    private Long creatorUserId;

    @Schema(description = "仅看与我相关（负责或执行）")
    private Boolean mineOnly;

    @Schema(description = "是否逾期（计划截止已过且未完成/取消）")
    private Boolean overdueOnly;
}
