package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "已发布文章列表行（投放明细 + 主表）")
public class GeoContentPlacementArticleListVO {

    @Schema(description = "发布明细ID", example = "1")
    private Long id;

    @Schema(description = "投放主表ID", example = "10")
    private Long placementId;

    @Schema(description = "发布人用户ID", example = "1", nullable = true)
    private Long publisherUserId;

    @Schema(description = "发布人")
    private String publisherName;

    @Schema(description = "撰写人用户ID", example = "2", nullable = true)
    private Long ownerUserId;

    @Schema(description = "撰写人")
    private String ownerName;

    @Schema(description = "话题ID", example = "1", nullable = true)
    private Long topicId;

    @Schema(description = "话题")
    private String topicName;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "发布平台", example = "搜狐")
    private String platformName;

    @Schema(description = "内容形态（图文/视频）", example = "图文")
    private String contentForm;

    @Schema(description = "投放状态（投放成功/审核未通过/未投放）", example = "投放成功")
    private String publishStatus;

    @Schema(description = "投放链接")
    private String publishUrl;

    @Schema(description = "发布时间", example = "2026-09-03 14:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "引用条数", example = "0")
    private Integer citeCount;

    @Schema(description = "创建时间", example = "2026-09-15 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
