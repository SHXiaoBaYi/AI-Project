package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "内容投放-发布人周看板明细行")
public class GeoContentPublisherWeekDetailVO {

    @Schema(description = "发布详情ID", example = "11")
    private Long itemId;

    @Schema(description = "内容投放ID", example = "1")
    private Long placementId;

    @Schema(description = "发布人", example = "李金瑜")
    private String publisherName;

    @Schema(description = "话题", example = "新疆羊肉")
    private String topicName;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "发布平台", example = "搜狐")
    private String platformName;

    @Schema(description = "内容形态（图文/视频）", example = "图文")
    private String contentForm;

    @Schema(description = "投放状态", example = "投放成功")
    private String publishStatus;

    @Schema(description = "投放链接")
    private String publishUrl;

    @Schema(description = "发布时间", example = "2026-09-10")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishTime;
}
