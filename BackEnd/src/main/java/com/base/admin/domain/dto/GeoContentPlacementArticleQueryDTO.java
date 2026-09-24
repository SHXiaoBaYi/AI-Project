package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "已发布文章（投放明细）分页查询")
public class GeoContentPlacementArticleQueryDTO extends PageQuery {

    @Schema(description = "发布人用户ID", example = "1")
    private Long publisherUserId;

    @Schema(description = "撰写人用户ID", example = "2")
    private Long ownerUserId;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "投放主表ID（精确过滤）", example = "12")
    private Long placementId;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "发布平台（内容发布平台）")
    private String platformName;

    @Schema(description = "投放状态（投放成功/审核未通过/待投放/未投放）", example = "投放成功")
    private String publishStatus;

    @Schema(description = "发布时间起（含当日）", example = "2026-01-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate publishTimeStart;

    @Schema(description = "发布时间止（含当日）", example = "2026-12-31")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate publishTimeEnd;

    @Schema(description = "引用筛选：1有引用 0无引用", example = "1", nullable = true)
    private Integer cited;

    @Schema(description = "引用条数排序：asc / desc", example = "desc", nullable = true)
    private String citeSort;
}
