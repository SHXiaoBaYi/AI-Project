package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内容投放-发布人周看板行")
public class GeoContentPublisherWeekRowVO {

    @Schema(description = "周标签", example = "2026-W37")
    private String weekLabel;

    @Schema(description = "周开始", example = "2026-09-08")
    private String weekStart;

    @Schema(description = "周结束", example = "2026-09-14")
    private String weekEnd;

    @Schema(description = "发布人用户ID", example = "1", nullable = true)
    private Long publisherUserId;

    @Schema(description = "发布人", example = "李金瑜")
    private String publisherName;

    @Schema(description = "产出篇数（本周图文已提交：投放成功+审核未通过）", example = "5")
    private Integer producedCount;

    @Schema(description = "待审数（当前图文审核未通过）", example = "1")
    private Integer pendingReviewCount;

    @Schema(description = "是否有待审", example = "true")
    private Boolean hasPendingReview;

    @Schema(description = "已发布篇数（本周图文投放成功）", example = "4")
    private Integer publishedCount;

    @Schema(description = "待产出篇数（当前未投放）", example = "3")
    private Integer pendingProduceCount;

    @Schema(description = "视频已发布数（本周）", example = "2")
    private Integer videoPublishedCount;

    @Schema(description = "视频待审数（当前）", example = "0")
    private Integer videoPendingReviewCount;

    @Schema(description = "视频是否有待审", example = "false")
    private Boolean hasVideoPendingReview;
}
