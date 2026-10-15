package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "员工收录看板豆腐块图表查询")
public class BoardTaskTofuQueryDTO {

    @Schema(description = "开始日期", example = "2014-01-01")
    private String startDate;

    @Schema(description = "结束日期", example = "2014-01-31")
    private String endDate;

    @Schema(description = "时间粒度 day/week/month/year", example = "week")
    private String grain;

    @Schema(description = "图表类型：publishCount/citeRate/employeeCiteCompare/employeeCiteMom/employeeCiteYoy/topicCiteCount")
    private String chartType;

    @Schema(description = "话题ID（下钻）")
    private Long topicId;

    @Schema(description = "目标问题（下钻）")
    private String targetQuestion;

    @Schema(description = "发布人用户ID（下钻，话题发布数量用）")
    private Long publisherUserId;

    @Schema(description = "发布人名称（兜底键）")
    private String publisherName;

    @Schema(description = "撰写人用户ID（下钻，员工AI收录对比用）", example = "2")
    private Long writerUserId;

    @Schema(description = "撰写人名称（兜底键）", example = "甄德明")
    private String writerName;

    @Schema(description = "内容发布平台（下钻）")
    private String contentPlatform;

    @Schema(description = "AI平台（下钻）")
    private String aiPlatform;
}
