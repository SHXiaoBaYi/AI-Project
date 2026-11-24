package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "黑名单人才")
public class HrTalentBlacklistVO {

    @Schema(description = "黑名单记录ID")
    private Long id;

    @Schema(description = "姓名")
    private String displayName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "关联岗位与阶段")
    private String jobSummary;

    @Schema(description = "移入原因")
    private String reason;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "移入时间")
    private String blacklistedAt;
}
