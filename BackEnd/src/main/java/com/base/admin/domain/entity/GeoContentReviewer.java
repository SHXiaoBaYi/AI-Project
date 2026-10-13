package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("geo_content_reviewer")
@Schema(description = "GEO文章审核人明细")
public class GeoContentReviewer extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "审核轮次ID", example = "1")
    private Long reviewId;

    @Schema(description = "投放主表ID", example = "10")
    private Long placementId;

    @Schema(description = "审核人用户ID", example = "3")
    private Long userId;

    @Schema(description = "审核人", example = "李四")
    private String userName;

    @Schema(description = "pending/approved/rejected", example = "pending")
    private String result;

    @TableField("comment_text")
    @Schema(description = "审核意见")
    private String commentText;

    @Schema(description = "操作时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actedAt;
}
