package com.base.admin.domain.dto;

import com.base.admin.domain.vo.UserDataScopeGeoVO;
import com.base.admin.domain.vo.UserDataScopeHrVO;
import com.base.admin.domain.vo.UserDataScopeTaskVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "保存用户数据权限配置")
public class UserDataScopeSaveDTO {

    @NotNull(message = "请选择用户")
    @Schema(description = "被配置的系统用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "全局全量业务数据", example = "false")
    private Boolean globalAll;

    @Schema(description = "GEO 切片")
    private UserDataScopeGeoVO geo;

    @Schema(description = "招聘切片")
    private UserDataScopeHrVO hr;

    @Schema(description = "任务切片")
    private UserDataScopeTaskVO task;
}
