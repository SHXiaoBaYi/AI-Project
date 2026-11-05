package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "GEO自动监测-监控问题批量启停")
public class GeoAutoQuestionEnabledDTO {

    @NotEmpty(message = "请选择问题")
    @Schema(description = "问题ID列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2,3]")
    private List<Long> ids;

    @Schema(description = "1=启用 0=停用", example = "1")
    private Integer enabled;
}
