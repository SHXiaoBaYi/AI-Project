package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_topic")
@Schema(description = "GEO话题")
public class GeoTopic extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "话题ID", example = "1")
    private Long id;

    @Schema(description = "话题名称", example = "送礼")
    private String topicName;

    @Schema(description = "开始优化时间", example = "8月第2周")
    private String optimizeWeek;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否演示数据 1=是 0=否", example = "0")
    private Integer isDemo;
}
