package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO日监测分页查询")
public class GeoDailyQueryDTO extends PageQuery {

    @Schema(description = "开始日期", example = "2026-09-01")
    private LocalDate startDate;

    @Schema(description = "结束日期", example = "2026-09-30")
    private LocalDate endDate;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "关键字")
    private String keyword;

    @Schema(description = "平台（多选）")
    private List<String> platforms;
}
