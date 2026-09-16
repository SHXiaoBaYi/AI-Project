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
@TableName("geo_content_period_stat")
@Schema(description = "GEO内容投放周/月看板落库快照")
public class GeoContentPeriodStat extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "周期类型 WEEK/MONTH")
    private String periodType;

    @Schema(description = "周期唯一键")
    private String periodKey;

    @Schema(description = "展示标签")
    private String periodLabel;

    @Schema(description = "周期开始")
    private LocalDate periodStart;

    @Schema(description = "周期结束")
    private LocalDate periodEnd;

    @Schema(description = "发布人用户ID")
    private Long publisherUserId;

    @Schema(description = "发布人名称快照")
    private String publisherName;

    @Schema(description = "话题ID")
    private Long topicId;

    @Schema(description = "话题名称快照")
    private String topicName;

    @Schema(description = "发布平台，*=汇总")
    private String publishPlatform;

    @Schema(description = "成功发布数")
    private Integer successCount;

    @Schema(description = "被收录数")
    private Integer citedCount;

    @Schema(description = "引用次数")
    private Integer citeHitCount;

    @Schema(description = "收录率%")
    private BigDecimal citeRate;

    @Schema(description = "落库时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lockedAt;
}
