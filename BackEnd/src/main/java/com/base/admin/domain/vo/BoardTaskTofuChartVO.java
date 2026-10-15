package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "员工收录看板豆腐块图表")
public class BoardTaskTofuChartVO {

    @Schema(description = "图表类型")
    private String chartType;

    @Schema(description = "当前层级 topic/question/employee/writer/contentPlatform/aiPlatform")
    private String level;

    @Schema(description = "系列字段（与 level 一致）")
    private String seriesField;

    @Schema(description = "时间粒度")
    private String grain;

    @Schema(description = "指标说明", example = "发布数量")
    private String metricLabel;

    @Schema(description = "话题ID")
    private Long topicId;

    @Schema(description = "话题名称")
    private String topicName;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "发布人用户ID")
    private Long publisherUserId;

    @Schema(description = "发布人名称")
    private String publisherName;

    @Schema(description = "撰写人用户ID", example = "2")
    private Long writerUserId;

    @Schema(description = "撰写人名称", example = "甄德明")
    private String writerName;

    @Schema(description = "内容发布平台")
    private String contentPlatform;

    @Schema(description = "AI平台")
    private String aiPlatform;

    @Schema(description = "分组柱数据（横轴=日期，系列=当前层级）")
    private List<GeoChartPointVO> chart = new ArrayList<>();
}
