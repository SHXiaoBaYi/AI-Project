package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "收银培训答疑提问")
public class TrainAssistantAskDTO {

    @NotBlank
    @Schema(description = "用户问题")
    private String question;

    @Schema(description = "是否检索旧版；未找到答案时前端二次确认后传 true")
    private Boolean searchOld;

    @Schema(description = "文档分类，默认 cashier")
    private String category;
}
