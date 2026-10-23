package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO文章审核AI预审结果")
public class GeoContentReviewAiVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "审核轮次ID", example = "1")
    private Long reviewId;

    @Schema(description = "投放主表ID", example = "10")
    private Long placementId;

    @Schema(description = "pending/running/done/failed", example = "done")
    private String status;

    @Schema(description = "approve/reject", example = "reject")
    private String suggestion;

    @Schema(description = "0-100", example = "72")
    private Integer score;

    @Schema(description = "风险摘要")
    private String summary;

    @Schema(description = "划词建议")
    private List<GeoContentReviewAiIssueVO> issues = new ArrayList<>();

    @Schema(description = "AI厂商")
    private String provider;

    @Schema(description = "模型名")
    private String modelName;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "是否已采纳划词", example = "false")
    private Boolean adopted;

    @Schema(description = "accurate/inaccurate")
    private String feedback;

    @Schema(description = "反馈说明")
    private String feedbackRemark;

    @Schema(description = "开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @Schema(description = "结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    @Schema(description = "该撰写人是否启用 AI 预审")
    private Boolean aiPreReview;

    @Schema(description = "assist / skip_stage1_on_pass")
    private String aiMode;

    @Schema(description = "AI 未完成前是否禁止人工通过")
    private Boolean aiBlockApprove;
}
