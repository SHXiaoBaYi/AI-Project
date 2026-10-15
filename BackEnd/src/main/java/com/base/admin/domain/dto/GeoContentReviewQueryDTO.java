package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "审核记录筛选")
public class GeoContentReviewQueryDTO {

    @Schema(description = "轮次状态 pending/approved/rejected", example = "approved")
    private String status;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "目标问题（模糊）", example = "中秋送礼")
    private String targetQuestion;

    @Schema(description = "正文标题（模糊）", example = "选购指南")
    private String contentTitle;

    @Schema(description = "发起人用户ID", example = "2")
    private Long submitterUserId;

    @Schema(description = "审核人用户ID", example = "3")
    private Long reviewerUserId;
}
