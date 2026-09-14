package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_year_target")
@Schema(description = "GEO全年目标配置")
public class GeoYearTarget extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "时间段名称", example = "全年")
    private String periodLabel;

    @Schema(description = "区间开始", example = "2026-01-01", nullable = true)
    private LocalDate periodStart;

    @Schema(description = "区间结束", example = "2026-12-31", nullable = true)
    private LocalDate periodEnd;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "目标达成率%", example = "80.00")
    private BigDecimal targetRate;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
