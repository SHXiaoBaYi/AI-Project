package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "把日程转给或增加一名面试官")
public class HrInviteTransferDTO {

    @NotNull
    @Schema(description = "目标面试官")
    private Long interviewerUserId;
}
