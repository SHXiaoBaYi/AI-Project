package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "我的撰写记录筛选")
public class GeoContentWritingQueryDTO {

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "目标问题（模糊）", example = "中秋送礼")
    private String targetQuestion;

    @Schema(description = "正文标题（模糊）", example = "选购指南")
    private String contentTitle;

    @Schema(description = "稿件状态 drafting/pending_review/revising/queue/published", example = "queue")
    private String contentStatus;
}
