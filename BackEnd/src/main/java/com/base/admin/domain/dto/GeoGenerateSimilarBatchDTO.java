package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量生成相似目标问题请求")
public class GeoGenerateSimilarBatchDTO {

    @NotEmpty(message = "请选择要生成的内容投放")
    @Schema(description = "内容投放ID列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2,3]")
    private List<Long> ids;

    @Schema(description = "AI 厂商：local | tongyi | deepseek | zhipu | moonshot；空则用系统默认", example = "tongyi")
    private String provider;
}
