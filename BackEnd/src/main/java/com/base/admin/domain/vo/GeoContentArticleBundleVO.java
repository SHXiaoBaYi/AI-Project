package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "一个问题下的统一稿与各平台文章")
public class GeoContentArticleBundleVO {

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "话题名称", example = "新疆羊肉")
    private String topicName;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "各平台默认共用的统一稿")
    private GeoContentDraftVO shared;

    @Schema(description = "各内容平台上的文章")
    private List<GeoContentArticleVO> articles = new ArrayList<>();
}
