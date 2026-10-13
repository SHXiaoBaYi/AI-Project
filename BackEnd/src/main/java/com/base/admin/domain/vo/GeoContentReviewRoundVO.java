package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "文章某一轮审核（含提审正文快照）")
public class GeoContentReviewRoundVO {

    @Schema(description = "审核轮次ID；当前稿时为 null", example = "1", nullable = true)
    private Long reviewId;

    @Schema(description = "条目类型：draft=当前稿 review=某轮提审快照", example = "review")
    private String entryType;

    @Schema(description = "第几轮；当前稿为空", example = "1", nullable = true)
    private Integer roundNo;

    @Schema(description = "pending/approved/rejected；当前稿为空", example = "rejected", nullable = true)
    private String status;

    @Schema(description = "发起人")
    private String submitterName;

    @Schema(description = "提审说明")
    private String submitRemark;

    @Schema(description = "提审时正文版本", example = "3")
    private Long contentVersion;

    @Schema(description = "提审时正文标题")
    private String contentTitle;

    @Schema(description = "提审时正文格式", example = "text/lake")
    private String contentFormat;

    @Schema(description = "提审时正文快照")
    private String contentBody;

    @Schema(description = "是否有正文快照", example = "true")
    private Boolean hasSnapshot;

    @Schema(description = "审核人明细")
    private List<GeoContentReviewerItemVO> reviewers;

    @Schema(description = "审核人摘要")
    private String reviewerSummary;

    @Schema(description = "提审时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
