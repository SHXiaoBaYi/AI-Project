package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 浏览器自动监测账号就绪状态（不返回密码） */
@Data
@Schema(description = "浏览器自动监测账号状态")
public class GeoBrowserAccountVO {

    @Schema(description = "平台名")
    private String platform;

    @Schema(description = "平台账号ID")
    private Long accountId;

    @Schema(description = "登录账号（脱敏展示）")
    private String account;

    @Schema(description = "是否已配置密码")
    private boolean hasPassword;

    @Schema(description = "登录方式 PASSWORD/STORAGE_STATE")
    private String loginType;

    @Schema(description = "登录态 NONE/READY/FAILED/EXPIRED")
    private String loginStatus;

    @Schema(description = "登录态是否可直接复用")
    private boolean ready;

    @Schema(description = "登录态文件")
    private String statePath;

    @Schema(description = "最近登录成功时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginAt;

    @Schema(description = "最近登录失败原因")
    private String error;

    @Schema(description = "连续失败次数")
    private Integer failCount;
}
