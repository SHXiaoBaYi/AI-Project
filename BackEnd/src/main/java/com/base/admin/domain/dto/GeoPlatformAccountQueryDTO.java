package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO平台账号分页查询")
public class GeoPlatformAccountQueryDTO extends PageQuery {

    @Schema(description = "平台ID", example = "1")
    private Long platformId;

    @Schema(description = "登录账号（模糊）")
    private String account;

    @Schema(description = "管理人用户ID")
    private Long managerUserId;

    @Schema(description = "当前持有人用户ID")
    private Long holderUserId;

    @Schema(description = "开通人用户ID")
    private Long openerUserId;

    @Schema(description = "是否充值 1=是 0=否")
    private Integer recharged;

    @Schema(description = "是否已验证 1=是 0=否")
    private Integer verified;

    @Schema(description = "账号状态：正常/停用/过期")
    private String accountStatus;

    @Schema(description = "登录方式")
    private String loginMethod;
}
