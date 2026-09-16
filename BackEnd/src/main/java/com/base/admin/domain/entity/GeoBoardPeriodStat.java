package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_board_period_stat")
@Schema(description = "GEO周/月/年看板落库快照")
public class GeoBoardPeriodStat extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "周期类型 WEEK/MONTH/YEAR", example = "WEEK")
    private String periodType;

    @Schema(description = "周期唯一键", example = "2026-W36")
    private String periodKey;

    @Schema(description = "展示标签", example = "2026年第36周")
    private String periodLabel;

    @Schema(description = "周期开始", example = "2026-09-01")
    private LocalDate periodStart;

    @Schema(description = "周期结束", example = "2026-09-07")
    private LocalDate periodEnd;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "话题名称快照", example = "送礼")
    private String topicName;

    @Schema(description = "平台", example = "豆包")
    private String platform;

    @Schema(description = "样本数", example = "12")
    private Integer sampleCount;

    @Schema(description = "露出率%", example = "37.50")
    private BigDecimal mentionRate;

    @Schema(description = "首位露出率%", example = "70.00")
    private BigDecimal firstMentionRate;

    @Schema(description = "推荐次数", example = "5")
    private Integer recommendCount;

    @Schema(description = "竞品TOP")
    private String competitorTop;

    @Schema(description = "引用平台TOP")
    private String citePlatformTop;

    @Schema(description = "目标%（年度）", example = "80.00", nullable = true)
    private BigDecimal targetRate;

    @Schema(description = "实际达成%（年度）", example = "86.00", nullable = true)
    private BigDecimal actualRate;

    @Schema(description = "达成率%（年度）", example = "107.50", nullable = true)
    private BigDecimal achieveRate;

    @Schema(description = "落库时间", example = "2026-09-15 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lockedAt;
}
