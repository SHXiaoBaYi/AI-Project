package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "任务模块数据权限切片")
public class UserDataScopeTaskVO {

    @Schema(description = "是否启用任务切片", example = "true")
    private Boolean enabled = false;

    @Schema(description = "可见任务类型 ID（空=不按类型限制）")
    private List<Long> taskTypeIds = new ArrayList<>();

    @Schema(description = "仅本人负责的任务", example = "false")
    private Boolean ownerOnly = false;

    @Schema(description = "仅本人办理的任务", example = "false")
    private Boolean assigneeOnly = false;
}
