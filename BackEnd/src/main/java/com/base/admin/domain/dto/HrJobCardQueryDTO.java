package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "岗位信息卡片筛选")
public class HrJobCardQueryDTO {

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;

    @Schema(description = "部门，含下级", example = "1", nullable = true)
    private Long deptId;

    @Schema(description = "岗位名称")
    private String jobName;

    @Schema(description = "负责人用户ID", example = "3", nullable = true)
    private Long ownerUserId;

    @Schema(description = "岗位状态：PENDING待招 / ACTIVE进行中 / CLOSED已关闭")
    private String cardStatus;

    @Schema(description = "紧急程度：1紧急 2优先 3常规", example = "1", nullable = true)
    private Integer priority;

    @Schema(description = "岗位优先级编码 P0/P1")
    private String jobPriorityCode;

    @Schema(description = "工作地址字典ID", example = "12", nullable = true)
    private Long workAddressId;
}
