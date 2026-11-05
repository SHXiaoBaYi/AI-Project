package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "GEO自动监测-结果列表行（不含回答原文）")
public class GeoAutoResultVO {

    @Schema(description = "结果ID", example = "1")
    private Long id;

    @Schema(description = "批次ID", example = "1")
    private Long runId;

    @Schema(description = "问题ID", example = "1")
    private Long questionId;

    @Schema(description = "问题文本", example = "新疆适合寄内地的礼品有哪些？")
    private String questionText;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "话题名称", example = "送礼")
    private String topicName;

    @Schema(description = "平台", example = "豆包")
    private String platform;

    @Schema(description = "通道：API/BROWSER", example = "API")
    private String channel;

    @Schema(description = "实际调用的模型名", example = "doubao-seed-1-6-250615")
    private String answerModel;

    @Schema(description = "监测归属日期", example = "2026-11-05")
    private LocalDate runDate;

    @Schema(description = "是否提及 1=是 0=否", example = "1")
    private Integer mentioned;

    @Schema(description = "排名", example = "2")
    private Integer rankNo;

    @Schema(description = "推荐状态", example = "出现且推荐")
    private String recommendStatus;

    @Schema(description = "情感", example = "正面")
    private String sentiment;

    @Schema(description = "品牌首次出现段位", example = "1")
    private Integer mentionPosition;

    @Schema(description = "负面/错误内容")
    private String negativeContent;

    @Schema(description = "出现的竞品，逗号分隔")
    private String competitors;

    @Schema(description = "状态：SUCCESS/SKIPPED_MANUAL/JUDGE_FAILED/CALL_FAILED", example = "SUCCESS")
    private String status;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "耗时毫秒", example = "3200")
    private Integer latencyMs;

    @Schema(description = "回写日监测行ID", example = "100")
    private Long dailyId;

    @Schema(description = "创建时间（监测发生时间）")
    private String createTime;
}
