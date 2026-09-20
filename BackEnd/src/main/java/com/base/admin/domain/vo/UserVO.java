package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "用户详情")
public class UserVO {

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "昵称", example = "管理员")
    private String nickname;

    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "性别（0=未知 1=男 2=女）", example = "1")
    private Integer gender;

    @Schema(description = "状态（0=正常 1=停用）", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间", example = "2024-01-01 00:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "关联角色列表")
    private List<RoleSimpleVO> roles;

    @Schema(description = "是否已绑定钉钉，1是 0否")
    private Integer dingtalkBound;

    @Schema(description = "企业内钉钉 userid")
    private String dingtalkUserId;

    @Schema(description = "钉钉 unionId")
    private String dingtalkUnionId;
}
