package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO自动监测-从历史数据洗入问题库结果")
public class GeoAutoSeedResultVO {

    @Schema(description = "扫描到的候选问题数（日监测关键字+内容投放目标问题去重后）", example = "120")
    private int scanned;

    @Schema(description = "新导入数", example = "86")
    private int imported;

    @Schema(description = "已存在跳过数", example = "34")
    private int skipped;
}
