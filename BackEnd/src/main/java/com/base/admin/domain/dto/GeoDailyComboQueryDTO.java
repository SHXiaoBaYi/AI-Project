package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO 话题×关键字×平台 组合查询")
public class GeoDailyComboQueryDTO extends PageQuery {

    @Schema(description = "开始日期", example = "2026-09-01")
    private LocalDate startDate;

    @Schema(description = "结束日期", example = "2026-09-30")
    private LocalDate endDate;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "关键字（模糊，列表筛选用）")
    private String keyword;

    @Schema(description = "精确关键字（明细/折线必填）", example = "新疆适合寄内地的礼品")
    private String keywordExact;

    @Schema(description = "平台（单选，明细/折线用）", example = "豆包")
    private String platform;

    @Schema(description = "平台（多选，列表筛选用）")
    private List<String> platforms;
}
