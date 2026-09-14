package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户新增/修改请求参数")
public class UserDTO {

    @Schema(description = "用户ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long userId;

    @NotBlank(message = "Username cannot be empty")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan")
    private String username;

    @Schema(description = "密码（新增时必填）", example = "123456")
    private String password;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "性别（0=未知 1=男 2=女）", example = "1")
    private Integer gender;

    @Schema(description = "状态（0=正常 1=停用）", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "关联角色ID列表")
    private List<Long> roleIds;
}
