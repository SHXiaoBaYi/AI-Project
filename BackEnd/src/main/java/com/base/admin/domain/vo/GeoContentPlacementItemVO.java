package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "内容投放-平台发布明细")
public class GeoContentPlacementItemVO {

    @Schema(description = "明细ID", example = "1")
    private Long id;

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "发布平台", example = "搜狐")
    private String platformName;

    @Schema(description = "投放状态（投放成功/审核未通过/未投放）", example = "投放成功")
    private String publishStatus;

    @Schema(description = "投放链接")
    private String publishUrl;

    @Schema(description = "发布时间", example = "2026-09-03")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishTime;

    @Schema(description = "该平台关联引用条数", example = "2")
    private Integer citeCount;

    @Schema(description = "备注")
    private String remark;
}
