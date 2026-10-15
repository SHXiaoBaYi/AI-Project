package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 生成文章结果")
public class GeoContentAiGenerateVO {

    @Schema(description = "文章标题")
    private String contentTitle;

    @Schema(description = "Lake 正文")
    private String contentBody;

    @Schema(description = "所用模型展示名")
    private String sourceAiModel;

    @Schema(description = "召回卖点条数")
    private Integer usedPointCount;

    @Schema(description = "是否已写入统一稿/独立稿")
    private Boolean applied;

    @Schema(description = "保存后的正文版本（若已 apply）")
    private Long contentVersion;
}
