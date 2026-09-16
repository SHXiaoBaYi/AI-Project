package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "生成相似目标问题请求")
public class GeoGenerateSimilarDTO {

    @Schema(description = "AI 厂商：local | tongyi | deepseek | zhipu | moonshot；空则用系统默认", example = "tongyi")
    private String provider;
}
