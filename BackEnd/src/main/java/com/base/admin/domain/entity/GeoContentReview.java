package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_content_review")
@Schema(description = "GEO文章审核轮次")
public class GeoContentReview extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "投放主表ID", example = "10")
    private Long placementId;

    @Schema(description = "第几轮提审（含退回重提）", example = "1")
    private Integer roundNo;

    @Schema(description = "审核阶段 1=一审 2=二审", example = "1")
    private Integer stageNo;

    @Schema(description = "pending/approved/rejected", example = "pending")
    private String status;

    @Schema(description = "发起人用户ID", example = "2", nullable = true)
    private Long submitterUserId;

    @Schema(description = "发起人", example = "甄德明")
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
}
