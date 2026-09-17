package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "任务执行人")
public class SysTaskAssigneeVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "展示名")
    private String userName;

    @Schema(description = "角色")
    private String roleLabel;

    @Schema(description = "个人是否完成")
    private Integer done;
}
