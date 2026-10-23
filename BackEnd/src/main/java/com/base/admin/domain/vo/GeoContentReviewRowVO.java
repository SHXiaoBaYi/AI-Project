package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "审核记录/待审核行")
public class GeoContentReviewRowVO {

    @Schema(description = "审核轮次ID", example = "1")
    private Long reviewId;

    @Schema(description = "投放主表ID", example = "10")
    private Long placementId;

    @Schema(description = "第几轮提审", example = "1")
    private Integer roundNo;

    @Schema(description = "审核阶段 1=一审 2=二审", example = "1")
    private Integer stageNo;

    @Schema(description = "轮次状态 pending/approved/rejected", example = "pending")
    private String status;

    @Schema(description = "话题")
    private String topicName;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "正文标题")
    private String contentTitle;

    @Schema(description = "发起人")
    private String submitterName;

    @Schema(description = "提审说明")
    private String submitRemark;

    @Schema(description = "我的审核结果（待审列表用）", example = "pending")
    private String myResult;

    @Schema(description = "审核人摘要，如 张三✓ 李四待审")
    private String reviewerSummary;

    @Schema(description = "审核人明细")
    private List<GeoContentReviewerItemVO> reviewers;

    @Schema(description = "提审时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "AI 预审摘要（列表用）")
    private GeoContentReviewAiVO ai;
}
