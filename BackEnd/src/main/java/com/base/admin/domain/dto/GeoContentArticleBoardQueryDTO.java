package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "文章发布/收录看板查询")
public class GeoContentArticleBoardQueryDTO {

    @Schema(description = "开始日期", example = "2026-08-01")
    private String startDate;

    @Schema(description = "结束日期", example = "2026-09-14")
    private String endDate;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "发布人用户ID（可选）", example = "1")
    private Long publisherUserId;

    @Schema(description = "发布人用户ID列表（多人对比）")
    private List<Long> publisherUserIds;

    @Schema(description = "发布内容平台（多选）")
    private List<String> publishPlatforms;

    @Schema(description = "AI平台（多选，筛收录）")
    private List<String> aiPlatforms;

    @Schema(description = "内容形态：图文/视频", example = "图文")
    private String contentForm;
}
