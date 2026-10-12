package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO任务看板维度汇总查询")
public class BoardGeoTaskAggQueryDTO extends BoardGeoTaskQueryDTO {

    @Schema(description = "汇总维度：topic=话题 / writer=撰写人 / taskType=任务类型",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "topic")
    private String dim;
}
