package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "角色新增/修改请求参数")
public class RoleDTO {

    @Schema(description = "角色ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long roleId;

    @NotBlank(message = "Role name cannot be empty")
    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "普通角色")
    private String roleName;

    @NotBlank(message = "Role key cannot be empty")
    @Schema(description = "角色标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "common")
    private String roleKey;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "状态（0=正常 1=停用）", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
