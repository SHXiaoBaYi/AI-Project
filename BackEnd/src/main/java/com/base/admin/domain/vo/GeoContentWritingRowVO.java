package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "我的撰写记录行")
public class GeoContentWritingRowVO {

    @Schema(description = "投放主表ID", example = "10")
    private Long placementId;

    @Schema(description = "话题")
    private String topicName;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "正文标题")
    private String contentTitle;

    @Schema(description = "稿件状态", example = "drafting")
    private String contentStatus;

    @Schema(description = "撰写人")
    private String ownerName;

    @Schema(description = "正文版本", example = "2")
    private Long contentVersion;

    @Schema(description = "当前进行中的审核轮次ID", example = "1", nullable = true)
    private Long currentReviewId;

    @Schema(description = "当前进行中的审核轮次", example = "1", nullable = true)
    private Integer currentRoundNo;

    @Schema(description = "最近一轮审核ID（含已退回/已通过）", example = "1", nullable = true)
    private Long latestReviewId;

    @Schema(description = "最近一轮审核状态 pending/approved/rejected", example = "rejected", nullable = true)
    private String latestReviewStatus;

    @Schema(description = "最近一轮审核轮次", example = "2", nullable = true)
    private Integer latestRoundNo;

    @Schema(description = "最近一轮审核人摘要，如 张三✓ 李四✗")
    private String reviewerSummary;

    @Schema(description = "退回意见摘要（便于改稿）")
    private String rejectSummary;

    @Schema(description = "最近一轮审核人明细")
    private List<GeoContentReviewerItemVO> reviewers;

    @Schema(description = "正文最近保存时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime contentUpdatedAt;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
