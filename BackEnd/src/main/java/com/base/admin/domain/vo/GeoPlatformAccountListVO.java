package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "GEO平台账号列表行（密码脱敏）")
public class GeoPlatformAccountListVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "平台ID")
    private Long platformId;

    @Schema(description = "平台名称")
    private String platformName;

    @Schema(description = "登录账号")
    private String account;

    @Schema(description = "脱敏密码", example = "****abcd")
    private String passwordMasked;

    @Schema(description = "是否已配置密码")
    private boolean hasPassword;

    @Schema(description = "平台昵称")
    private String accountNickname;

    @Schema(description = "管理人用户ID", nullable = true)
    private Long managerUserId;

    @Schema(description = "管理人")
    private String managerName;

    @Schema(description = "当前持有人用户ID", nullable = true)
    private Long holderUserId;

    @Schema(description = "当前持有人")
    private String holderName;

    @Schema(description = "开通人用户ID", nullable = true)
    private Long openerUserId;

    @Schema(description = "开通人")
    private String openerName;

    @Schema(description = "是否充值 1=是 0=否")
    private Integer recharged;

    @Schema(description = "开通时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime openTime;

    @Schema(description = "到期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @Schema(description = "平台登录方式")
    private String loginMethod;

    @Schema(description = "是否已验证 1=是 0=否")
    private Integer verified;

    @Schema(description = "验证方式")
    private String verifyMethod;

    @Schema(description = "绑定手机")
    private String bindPhone;

    @Schema(description = "绑定邮箱")
    private String bindEmail;

    @Schema(description = "账号状态")
    private String accountStatus;

    @Schema(description = "最近登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
