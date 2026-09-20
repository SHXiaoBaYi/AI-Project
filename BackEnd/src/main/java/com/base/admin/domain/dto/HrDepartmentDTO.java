package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "部门保存")
public class HrDepartmentDTO {

    @Schema(description = "部门ID，新增为空")
    private Long id;

    @Schema(description = "父部门，根为0")
    private Long parentId;

    @NotBlank
    @Schema(description = "部门名称")
    private String name;

    @Schema(description = "负责人用户ID")
    private Long leaderUserId;

    @Schema(description = "排序")
    private Integer sortOrder;
}
