package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "角色分配菜单请求参数")
public class RoleMenuDTO {

    @NotNull(message = "Role ID cannot be null")
    @Schema(description = "角色ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long roleId;

    @Schema(description = "菜单ID列表")
    private List<Long> menuIds;
}
