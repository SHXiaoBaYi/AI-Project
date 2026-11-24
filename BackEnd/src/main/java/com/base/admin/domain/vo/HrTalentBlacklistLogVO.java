package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "黑名单移入移出记录")
public class HrTalentBlacklistLogVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "IN 移入 / OUT 移出")
    private String action;

    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "姓名")
    private String displayName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "原因")
    private String reason;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "操作时间")
    private String operatedAt;
}
