package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "任务分配")
public class SysTaskAssignDTO {

    @Schema(description = "负责人用户ID（不传则取执行人第一位）", example = "1", nullable = true)
    private Long ownerUserId;

    @NotEmpty(message = "至少指定一名执行人")
    @Schema(description = "执行人用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1]")
    private List<Long> assigneeUserIds;
}
