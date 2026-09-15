package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO负责人选项（系统用户）")
public class GeoOwnerOptionVO {

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "展示名（昵称优先，否则用户名）", example = "张三")
    private String displayName;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @Schema(description = "昵称", example = "张三")
    private String nickname;
}
