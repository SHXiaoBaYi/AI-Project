package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "岗位信息卡片筛选")
public class HrJobCardQueryDTO {

    @Schema(description = "部门，含下级")
    private Long deptId;

    @Schema(description = "岗位状态：PENDING待招 / ACTIVE进行中 / CLOSED已关闭")
    private String cardStatus;

    @Schema(description = "紧急程度：1紧急 2优先 3常规")
    private Integer priority;
}
