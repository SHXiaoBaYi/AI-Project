package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "阶段调整记录")
public class HrStageAdjustLogVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "投递ID")
    private Long applicationId;

    @Schema(description = "候选人")
    private String candidateName;

    @Schema(description = "岗位")
    private String jobName;

    @Schema(description = "调整前阶段码")
    private String fromStage;

    @Schema(description = "调整前阶段")
    private String fromStageName;

    @Schema(description = "调整后阶段码")
    private String toStage;

    @Schema(description = "调整后阶段")
    private String toStageName;

    @Schema(description = "业务时间")
    private String businessAt;

    @Schema(description = "调整原因")
    private String reason;

    @Schema(description = "操作人用户ID")
    private Long operatorUserId;

    @Schema(description = "操作人账号")
    private String operatorUsername;

    @Schema(description = "操作人")
    private String operatorNickname;

    @Schema(description = "操作时间")
    private String operatedAt;
}
