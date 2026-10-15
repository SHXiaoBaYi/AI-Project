package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "撰写人发起两阶段串行审核（每阶段 1 人）")
public class GeoContentSubmitReviewDTO {

    @NotNull
    @Schema(description = "投放主表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long placementId;

    @Schema(description = "本阶段审核人；退回重提时可不传，沿用打回人", example = "3")
    private Long reviewerUserId;

    @Schema(description = "兼容旧入参，仅取第一人")
    private List<Long> reviewerUserIds;

    @Schema(description = "提审说明", example = "请帮忙看下第二段")
    private String remark;
}
