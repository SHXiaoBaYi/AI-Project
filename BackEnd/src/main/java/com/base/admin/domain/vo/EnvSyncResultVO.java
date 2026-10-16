package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@Schema(description = "环境同步结果")
public class EnvSyncResultVO {

    @Schema(description = "DATA / SCHEMA")
    private String kind;

    @Schema(description = "摘要")
    private String message;

    @Schema(description = "耗时毫秒", example = "3200")
    private long elapsedMs;

    @Schema(description = "明细行")
    @Builder.Default
    private List<String> details = new ArrayList<>();
}
