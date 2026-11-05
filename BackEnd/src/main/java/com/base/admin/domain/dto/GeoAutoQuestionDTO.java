package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "GEO自动监测-监控问题新增/修改")
public class GeoAutoQuestionDTO {

    @Schema(description = "问题ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotBlank(message = "监控问题不能为空")
    @Schema(description = "监控问题（目标问题原文）", requiredMode = Schema.RequiredMode.REQUIRED, example = "新疆适合寄内地的礼品有哪些？")
    private String questionText;

    @Schema(description = "归属话题ID（核心词）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long topicId;

    @Schema(description = "品牌词，逗号分隔，空=用全局默认", example = "小巴依")
    private String brandWords;

    @Schema(description = "竞品词，逗号分隔，空=用全局默认")
    private String competitorWords;

    @Schema(description = "监测频率：每日/每周", example = "每日")
    private String freq;

    @Schema(description = "负责人用户ID，0=未分配", example = "1")
    private Long ownerUserId;

    @Schema(description = "负责人展示名（界面传昵称）", example = "张三")
    private String ownerName;

    @Schema(description = "1=启用 0=停用", example = "1")
    private Integer enabled;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
