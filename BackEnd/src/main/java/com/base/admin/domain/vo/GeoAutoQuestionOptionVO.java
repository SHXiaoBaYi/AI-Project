package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "GEO自动监测-问题下拉项")
public class GeoAutoQuestionOptionVO {

    @Schema(description = "问题ID", example = "1")
    private Long id;

    @Schema(description = "问题文本", example = "新疆适合寄内地的礼品有哪些？")
    private String questionText;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "话题名称", example = "送礼")
    private String topicName;

    @Schema(description = "监测频率：每日/每周", example = "每日")
    private String freq;

    @Schema(description = "品牌词（逗号分隔，空=全局默认）")
    private String brandWords;

    @Schema(description = "竞品词（逗号分隔，空=全局默认）")
    private String competitorWords;
}
