package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO日监测分组（同一天+关键字）")
public class GeoDailyGroupVO {

    @Schema(description = "巡查日期", example = "2026-09-01")
    private LocalDate inspectDate;

    @Schema(description = "话题类型（日巡查/周巡查）", example = "日巡查")
    private String termType;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "话题名称")
    private String topicName;

    @Schema(description = "关键字")
    private String keyword;

    @Schema(description = "负责人用户ID", example = "1", nullable = true)
    private Long ownerUserId;

    @Schema(description = "负责人展示名")
    private String ownerName;

    @Schema(description = "各平台记录")
    private List<GeoDailyVO> items = new ArrayList<>();
}
