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
@TableName("geo_content_placement_cite")
@Schema(description = "GEO内容投放-AI平台引用情况")
public class GeoContentPlacementCite extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "关联平台投放明细ID", example = "1", nullable = true)
    private Long itemId;

    @Schema(description = "提问问题")
    private String askQuestion;

    @Schema(description = "AI平台名称", example = "豆包")
    private String aiPlatform;

    @Schema(description = "引用链接")
    private String citeUrl;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否演示数据 1=是 0=否", example = "0")
    private Integer isDemo;
}
