package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "招聘模块数据权限切片")
public class UserDataScopeHrVO {

    @Schema(description = "是否启用招聘切片", example = "true")
    private Boolean enabled = false;

    @Schema(description = "可见部门 ID（含下级；空=不按部门限制）")
    private List<Long> deptIds = new ArrayList<>();

    @Schema(description = "人员模式：DEFAULT / PERSON / SELF", example = "DEFAULT")
    private String personMode = "DEFAULT";

    @Schema(description = "指定可见人（personMode=PERSON）")
    private List<Long> targetUserIds = new ArrayList<>();
}
