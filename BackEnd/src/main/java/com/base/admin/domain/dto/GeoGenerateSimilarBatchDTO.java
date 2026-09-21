package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(value = 1, message = "生成数量至少 1 个")
    @Max(value = 10, message = "生成数量最多 10 个")
    @Schema(description = "每条源问题生成的条数，1 到 10", example = "5")
    private Integer count;
}
