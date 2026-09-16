package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "内容投放分页查询")
public class GeoContentPlacementQueryDTO extends PageQuery {

    @Schema(description = "发布人用户ID", example = "1")
    private Long publisherUserId;

    @Schema(description = "归属人用户ID", example = "2")
    private Long ownerUserId;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "来源（导入/手动新增/AI生成）", example = "AI生成")
    private String source;

    @Schema(description = "投放进度（投放完成/部分投放/未投放）", example = "部分投放")
    private String aggregateStatus;

    @Schema(description = "关联用户ID（发布人或归属人，一线视角筛选用）", example = "1")
    private Long relatedUserId;
}
