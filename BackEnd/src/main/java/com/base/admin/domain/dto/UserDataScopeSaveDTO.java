package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "保存用户数据权限配置")
public class UserDataScopeSaveDTO {

    @NotNull(message = "请选择用户")
    @Schema(description = "被配置的系统用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @NotBlank(message = "请选择模式")
    @Schema(description = "DEFAULT=沿用角色 PERSON=指定可见人 SELF=仅本人", requiredMode = Schema.RequiredMode.REQUIRED, example = "PERSON")
    private String mode;

    @Schema(description = "可见人员用户ID列表（mode=PERSON 时使用）")
    private List<Long> targetUserIds;
}
