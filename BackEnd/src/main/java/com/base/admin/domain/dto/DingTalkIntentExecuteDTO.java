package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "执行机器人 ActionCard 意图票据")
public class DingTalkIntentExecuteDTO {

    @NotBlank
    @Schema(description = "意图短码 ticket", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ticket;
}
