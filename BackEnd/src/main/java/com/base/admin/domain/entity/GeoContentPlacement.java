package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_content_placement")
@Schema(description = "GEO内容投放主表")
public class GeoContentPlacement extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "发布人用户ID", example = "1", nullable = true)
    private Long publisherUserId;

    @Schema(description = "发布人展示名", example = "李金瑜")
    private String publisherName;

    @Schema(description = "撰写人用户ID", example = "2", nullable = true)
    private Long ownerUserId;

    @Schema(description = "撰写人展示名", example = "甄德明")
    private String ownerName;

    @Schema(description = "话题ID", example = "1", nullable = true)
    private Long topicId;

    @Schema(description = "话题名称", example = "新疆羊肉")
    private String topicName;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "来源（导入/手动新增/AI生成）", example = "手动新增")
    private String source;

    @Schema(description = "AI生成时参照的内容投放ID", example = "1", nullable = true)
    private Long sourcePlacementId;

    @Schema(description = "AI生成所用模型展示名", example = "DeepSeek（deepseek-chat）", nullable = true)
    private String sourceAiModel;

    @Schema(description = "投放进度（投放完成/部分投放/未投放）", example = "部分投放")
    private String placementProgress;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否演示数据 1=是 0=否", example = "0")
    private Integer isDemo;

    @Schema(description = "稿件状态 drafting/pending_review/revising/approved/queue", example = "drafting")
    private String contentStatus;

    @Schema(description = "正文标题", example = "中秋送礼指南")
    private String contentTitle;

    @Schema(description = "内容格式（一期固定 text/lake）", example = "text/lake")
    private String contentFormat;

    @Schema(description = "Lake 正文")
    private String contentBody;

    @Schema(description = "正文乐观锁版本", example = "1")
    private Long contentVersion;

    @Schema(description = "正文最近保存人", example = "admin")
    private String contentUpdatedBy;

    @Schema(description = "正文最近保存时间", example = "2026-10-12 14:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime contentUpdatedAt;
}
