package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_platform_account")
@Schema(description = "GEO平台账号")
public class GeoPlatformAccount extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "平台ID")
    private Long platformId;

    @Schema(description = "平台名称（冗余）")
    private String platformName;

    @Schema(description = "登录账号")
    private String account;

    @Schema(description = "登录密码")
    private String password;

    @Schema(description = "平台昵称/展示名")
    private String accountNickname;

    @Schema(description = "管理人用户ID", nullable = true)
    private Long managerUserId;

    @Schema(description = "管理人展示名")
    private String managerName;

    @Schema(description = "当前持有人用户ID", nullable = true)
    private Long holderUserId;

    @Schema(description = "当前持有人展示名")
    private String holderName;

    @Schema(description = "开通人用户ID", nullable = true)
    private Long openerUserId;

    @Schema(description = "开通人展示名")
    private String openerName;

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
