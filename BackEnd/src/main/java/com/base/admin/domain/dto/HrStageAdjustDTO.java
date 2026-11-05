package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "调整候选人阶段")
public class HrStageAdjustDTO {

    @NotNull(message = "请选择候选人")
    @Schema(description = "投递ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long applicationId;

    @NotBlank(message = "请选择阶段")
    @Schema(description = "目标阶段码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stageCode;

    @NotNull(message = "请填写业务日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "业务时间：已入职为入职日，面试阶段为面试时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime businessAt;

    @NotBlank(message = "请填写调整原因")
    @Size(max = 500, message = "调整原因不能超过500字")
    @Schema(description = "调整原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
