package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "最近一次巡查日期")
public class GeoLatestDateVO {

    @Schema(description = "最近更新记录对应的巡查日期", example = "2026-09-01")
    private LocalDate inspectDate;
}
