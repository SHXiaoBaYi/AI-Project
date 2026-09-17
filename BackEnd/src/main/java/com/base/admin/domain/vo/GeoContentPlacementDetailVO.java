package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "内容投放详情（含平台与引用）")
public class GeoContentPlacementDetailVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "发布人")
    private String publisherName;

    @Schema(description = "发布人用户ID", example = "1", nullable = true)
    private Long publisherUserId;

    @Schema(description = "撰写人")
    private String ownerName;

    @Schema(description = "撰写人用户ID", example = "2", nullable = true)
    private Long ownerUserId;

    @Schema(description = "话题")
    private String topicName;

    @Schema(description = "话题ID", example = "1", nullable = true)
    private Long topicId;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "来源（导入/手动新增/AI生成）", example = "手动新增")
    private String source;

    @Schema(description = "AI生成时参照的内容投放ID", example = "12", nullable = true)
    private Long sourcePlacementId;

    @Schema(description = "AI生成参照的目标问题", nullable = true)
    private String sourceTargetQuestion;

    @Schema(description = "AI生成所用模型", example = "DeepSeek（deepseek-chat）", nullable = true)
    private String sourceAiModel;

    @Schema(description = "投放进度（投放完成/部分投放/未投放）", example = "部分投放")
    private String aggregateStatus;

    @Schema(description = "投放进度百分比（无发布详情时为空）", example = "50", nullable = true)
    private Integer publishProgress;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "平台发布明细")
    private List<GeoContentPlacementItemVO> items = new ArrayList<>();

    @Schema(description = "AI引用明细")
    private List<GeoContentPlacementCiteVO> cites = new ArrayList<>();

    @Schema(description = "任务完成证明/业务附件")
    private List<SysTaskFileVO> proofFiles = new ArrayList<>();
}
