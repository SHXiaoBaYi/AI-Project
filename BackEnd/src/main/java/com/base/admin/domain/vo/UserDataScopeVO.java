package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "用户数据权限配置")
public class UserDataScopeVO {

    @Schema(description = "被配置用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "全局全量业务数据", example = "false")
    private Boolean globalAll = false;

    @Schema(description = "摘要（列表展示）")
    private String summary;

    @Schema(description = "兼容旧字段：DEFAULT / PERSON / SELF", example = "DEFAULT")
    private String mode;

    @Schema(description = "兼容旧字段：可见人员用户ID")
    private List<Long> targetUserIds = new ArrayList<>();

    @Schema(description = "GEO 切片")
    private UserDataScopeGeoVO geo = new UserDataScopeGeoVO();

    @Schema(description = "招聘切片")
    private UserDataScopeHrVO hr = new UserDataScopeHrVO();

    @Schema(description = "任务切片")
    private UserDataScopeTaskVO task = new UserDataScopeTaskVO();
}
