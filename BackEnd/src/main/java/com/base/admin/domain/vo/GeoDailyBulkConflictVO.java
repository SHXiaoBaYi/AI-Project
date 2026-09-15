package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "GEO日监测批量保存-已统计冲突项")
public class GeoDailyBulkConflictVO {

    @Schema(description = "已有记录ID", example = "12", nullable = true)
    private Long id;

    @Schema(description = "巡查日期", example = "2026-09-15")
    private LocalDate inspectDate;

    @Schema(description = "平台", example = "豆包")
    private String platform;

    @Schema(description = "关键字", example = "新疆适合寄内地的礼品")
    private String keyword;

    @Schema(description = "话题ID", example = "1", nullable = true)
    private Long topicId;

    @Schema(description = "话题名称", example = "礼品")
    private String topicName;

    @Schema(description = "冲突原因", example = "该记录已被周/月/年统计，不可覆盖")
    private String reason;
}
