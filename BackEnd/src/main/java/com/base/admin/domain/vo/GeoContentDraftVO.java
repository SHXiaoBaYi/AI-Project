package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "GEO内容投放在线正文")
public class GeoContentDraftVO {

    @Schema(description = "投放主表ID", example = "1")
    private Long placementId;

    @Schema(description = "目标问题")
    private String targetQuestion;

    @Schema(description = "正文标题")
    private String contentTitle;

    @Schema(description = "稿件状态 drafting/pending_review/revising/approved/queue", example = "drafting")
    private String contentStatus;

    @Schema(description = "内容格式（一期固定 text/lake）", example = "text/lake")
    private String contentFormat;

    @Schema(description = "Lake 正文")
    private String contentBody;

    @Schema(description = "乐观锁版本", example = "1")
    private Long contentVersion;

    @Schema(description = "是否可编辑", example = "true")
    private boolean editable;

    @Schema(description = "正文最近保存人")
    private String contentUpdatedBy;

    @Schema(description = "正文最近保存时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime contentUpdatedAt;
}
