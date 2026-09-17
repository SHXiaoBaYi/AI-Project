package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "GEO看板查询")
public class GeoBoardQueryDTO {

    @Schema(description = "开始日期", example = "2026-08-01")
    private LocalDate startDate;

    @Schema(description = "结束日期", example = "2026-09-14")
    private LocalDate endDate;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "关键字")
    private String keyword;

    @Schema(description = "话题类型（日巡查/周巡查）", example = "日巡查")
    private String termType;

    @Schema(description = "平台（多选）")
    private List<String> platforms;

    @Schema(description = "时间粒度：day/week/month/year", example = "week")
    private String grain;
}
