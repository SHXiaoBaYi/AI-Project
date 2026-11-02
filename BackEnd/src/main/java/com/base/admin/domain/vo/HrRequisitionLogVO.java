package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "招聘需求操作日志")
public class HrRequisitionLogVO {

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "需求ID")
    private Long requisitionId;

    @Schema(description = "岗位名")
    private String jobName;

    @Schema(description = "动作 CREATE/UPDATE/DELETE/STATUS")
    private String action;

    @Schema(description = "动作中文")
    private String actionLabel;

    @Schema(description = "做了什么")
    private String summary;

    @Schema(description = "变更前JSON")
    private String beforeJson;

    @Schema(description = "变更后JSON")
    private String afterJson;

    @Schema(description = "操作人用户ID")
    private Long operatorUserId;

    @Schema(description = "操作人登录名")
    private String operatorUsername;

    @Schema(description = "操作人昵称")
    private String operatorNickname;

    @Schema(description = "操作时间")
    private String operatedAt;
}
