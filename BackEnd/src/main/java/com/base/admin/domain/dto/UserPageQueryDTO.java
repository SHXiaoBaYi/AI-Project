package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询参数")
public class UserPageQueryDTO extends PageQuery {

    @Schema(description = "用户名（模糊查询）")
    private String username;

    @Schema(description = "昵称（模糊查询）")
    private String nickname;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "角色ID列表（筛选）")
    private List<Long> roleIds;

    @Schema(description = "状态（0=正常 1=停用）", example = "0")
    private Integer status;
}
