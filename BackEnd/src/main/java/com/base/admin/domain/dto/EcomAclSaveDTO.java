package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "电商运营 ACL 保存（可按平台/店铺切片）")
public class EcomAclSaveDTO {

    @NotNull
    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "是否启用")
    private Boolean enabled = true;

    @Schema(description = "全部平台")
    private Boolean allPlatforms = false;

    @Schema(description = "全部店铺（仍受平台切片约束）")
    private Boolean allShops = false;

    @Schema(description = "平台切片，如 jd/tmall/douyin；allPlatforms=true 时忽略")
    private List<String> platforms = new ArrayList<>();

    @Schema(description = "店铺ID切片；allShops=true 时忽略")
    private List<Long> shopIds = new ArrayList<>();

    @Schema(description = "备注")
    private String remark;
}
