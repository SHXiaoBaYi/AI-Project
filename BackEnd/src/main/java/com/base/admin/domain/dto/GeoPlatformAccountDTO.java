package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "GEO平台账号新增/修改")
public class GeoPlatformAccountDTO {

    @Schema(description = "主键（新增不传，修改必传）", nullable = true)
    private Long id;

    @NotNull(message = "平台不能为空")
    @Schema(description = "平台ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long platformId;

    @NotBlank(message = "账号不能为空")
    @Schema(description = "登录账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "ops_douyin_01")
    private String account;

    @Schema(description = "登录密码；修改时留空表示不改")
    private String password;

    @Schema(description = "是否清空密码", example = "false")
    private Boolean clearPassword;

    @Schema(description = "平台昵称/展示名")
    private String accountNickname;

    @Schema(description = "管理人用户ID", nullable = true)
    private Long managerUserId;

    @Schema(description = "当前持有人用户ID", nullable = true)
    private Long holderUserId;

    @Schema(description = "开通人用户ID", nullable = true)
    private Long openerUserId;

    @Schema(description = "是否充值 1=是 0=否", example = "0")
    private Integer recharged;

    @Schema(description = "开通时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime openTime;

    @Schema(description = "到期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @Schema(description = "平台登录方式", example = "账号密码")
    private String loginMethod;

    @Schema(description = "是否在平台做了验证 1=是 0=否", example = "0")
    private Integer verified;

    @Schema(description = "验证方式", example = "手机实名")
    private String verifyMethod;

    @Schema(description = "绑定手机")
    private String bindPhone;

    @Schema(description = "绑定邮箱")
    private String bindEmail;

    @Schema(description = "账号状态：正常/停用/过期", example = "正常")
    private String accountStatus;

    @Schema(description = "最近登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
