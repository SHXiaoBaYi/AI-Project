package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "某一内容平台上的文章")
public class GeoContentArticleVO {

    @Schema(description = "平台文章ID", example = "1")
    private Long itemId;

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "发布平台", example = "搜狐")
    private String platformName;

    @Schema(description = "内容形态（图文/视频）", example = "图文")
    private String contentForm;

    @Schema(description = "正文来源 shared=统一稿 custom=独立稿", example = "shared")
    private String contentMode;

    @Schema(description = "审核状态 drafting/pending_review/revising/approved", example = "drafting")
    private String contentStatus;

    @Schema(description = "当前生效标题（统一稿或独立稿）")
    private String contentTitle;

    @Schema(description = "当前生效 Lake 正文")
    private String contentBody;

    @Schema(description = "独立稿版本；统一稿时为统一稿版本", example = "1")
    private Long contentVersion;

    @Schema(description = "当前状态是否可改正文", example = "true")
    private boolean editable;

    @Schema(description = "投放状态", example = "未投放")
    private String publishStatus;

    @Schema(description = "内部意见")
    private List<GeoContentOpinionVO> opinions = new ArrayList<>();
}
