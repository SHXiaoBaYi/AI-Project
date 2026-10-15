package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO任务看板下钻明细查询")
public class BoardGeoTaskDrillQueryDTO extends BoardGeoTaskQueryDTO {

    @Schema(description = "下钻维度：topic / writer / taskType",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "topic")
    private String dim;

    @Schema(description = "话题ID（dim=topic；0=未关联话题）", example = "1", nullable = true)
    private Long topicId;

    @Schema(description = "撰写人用户ID（dim=writer；0=未分配撰写人）", example = "2", nullable = true)
    private Long writerUserId;

    @Schema(description = "任务类型（dim=taskType）", example = "文章撰写")
    private String taskType;

    @Schema(description = "明细子筛：all / done / open", example = "all")
    private String subFilter;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize;
}
