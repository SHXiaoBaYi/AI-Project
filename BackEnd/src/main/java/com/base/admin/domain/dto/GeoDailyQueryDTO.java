package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Schema(description = "话题类型（日巡查/周巡查）", example = "日巡查")
    private String termType;

    @Schema(description = "负责人用户ID", example = "1")
    private Long ownerUserId;

    @Schema(description = "负责人")
    private String ownerName;

    @Schema(description = "平台（多选）")
    private List<String> platforms;

    @Schema(description = "是否露出（1=是 0=否）", example = "1")
    private Integer mentioned;

    @Schema(description = "排名最小值", example = "1")
    private Integer rankNoMin;

    @Schema(description = "排名最大值", example = "10")
    private Integer rankNoMax;

    @Schema(description = "推荐状态", example = "出现且推荐")
    private String recommendStatus;

    @Schema(description = "是否有截图（1=有 0=无）", example = "1")
    private Integer hasScreenshot;

    @Schema(description = "是否有第三方链接（1=有 0=无）", example = "1")
    private Integer hasThirdPartyUrl;

    @Schema(description = "竞品（模糊）")
    private String competitors;

    @Schema(description = "是否已被周/月/年统计（1=已统计 0=未统计）", example = "0")
    private Integer boardLocked;

    @Schema(description = "更新时间起", example = "2026-09-01 00:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTimeStart;

    @Schema(description = "更新时间止", example = "2026-09-15 23:59:59")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTimeEnd;
}
