package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_content_placement_item")
@Schema(description = "GEO内容投放-发布平台明细")
public class GeoContentPlacementItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "发布平台", example = "搜狐")
    private String platformName;

    @Schema(description = "内容形态（图文/视频）", example = "图文")
    private String contentForm;

    @Schema(description = "投放状态（投放成功/审核未通过/未投放）", example = "投放成功")
    private String publishStatus;

    @Schema(description = "投放链接")
    private String publishUrl;

    @Schema(description = "发布时间", example = "2026-09-03")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishTime;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
