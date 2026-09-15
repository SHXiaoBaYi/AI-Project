package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线会话表：一用户一行，不做逻辑删除（避免 soft-delete 后 insert 主键冲突）。
 */
@Data
@TableName("sys_user_online")
@Schema(description = "用户在线会话")
public class SysUserOnline {

    @TableId(type = IdType.INPUT)
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

    @Schema(description = "登录时间", example = "2026-09-15 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginTime;

    @Schema(description = "过期时间", example = "2026-09-16 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
}
