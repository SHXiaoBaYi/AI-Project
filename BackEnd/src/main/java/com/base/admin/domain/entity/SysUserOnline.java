package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_online")
@Schema(description = "用户在线会话")
public class SysUserOnline {

    @TableId
    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "JWT jti")
    private String tokenId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "登录IP")
    private String ip;

    @Schema(description = "User-Agent")
    private String userAgent;

    @Schema(description = "登录时间")
    private LocalDateTime loginTime;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;
}
