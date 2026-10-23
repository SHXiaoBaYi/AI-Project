package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_content_review_ai")
@Schema(description = "GEO文章审核AI预审结果")
public class GeoContentReviewAi extends BaseEntity {

    @TableId(type = IdType.AUTO)
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

    @Schema(description = "划词建议 JSON")
    private String issuesJson;

    @Schema(description = "AI厂商")
    private String provider;

    @Schema(description = "模型名")
    private String modelName;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "是否已采纳划词 1=是", example = "0")
    private Integer adopted;

    @Schema(description = "accurate/inaccurate")
    private String feedback;

    @Schema(description = "反馈说明")
    private String feedbackRemark;

    @Schema(description = "反馈人")
    private String feedbackBy;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;
}
