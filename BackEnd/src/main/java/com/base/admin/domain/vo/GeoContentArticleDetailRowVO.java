package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文章发布/收录下钻明细行")
public class GeoContentArticleDetailRowVO {

    @Schema(description = "投放主表ID")
    private Long placementId;

    @Schema(description = "平台明细ID")
    private Long itemId;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "话题")
    private String topicName;

    @Schema(description = "发布人")
    private String publisherName;

    @Schema(description = "发布平台")
    private String publishPlatform;

    @Schema(description = "内容形态")
    private String contentForm;

    @Schema(description = "投放状态")
    private String publishStatus;

    @Schema(description = "发布时间")
    private String publishTime;

    @Schema(description = "投放链接")
    private String publishUrl;

    @Schema(description = "AI平台")
    private String aiPlatform;

    @Schema(description = "引用链接")
    private String citeUrl;

    @Schema(description = "提问问题")
    private String askQuestion;

    @Schema(description = "引用次数")
    private Integer citeCount;
}
