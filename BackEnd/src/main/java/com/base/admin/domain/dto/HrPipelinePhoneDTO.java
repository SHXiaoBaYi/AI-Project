package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "电话沟通记录")
public class HrPipelinePhoneDTO {

    @NotNull
    @Schema(description = "投递ID")
    private Long applicationId;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "沟通时间")
    private LocalDateTime calledAt;

    @NotBlank
    @Schema(description = "沟通结果 PASS合适 / FAIL不合适")
    private String result;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "约面时间（合适时填写）")
    private LocalDateTime interviewAt;

    @Schema(description = "不合适原因（淘汰时填写）")
    private String rejectReason;

    @Schema(description = "备注")
    private String remark;
}
