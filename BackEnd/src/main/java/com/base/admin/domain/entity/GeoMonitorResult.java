package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_monitor_result")
@Schema(description = "GEO自动监测-监测明细")
public class GeoMonitorResult extends BaseEntity {

    /** 成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 人工优先跳过（当日已有人工记录且任务未开覆盖） */
    public static final String STATUS_SKIPPED_MANUAL = "SKIPPED_MANUAL";
    /** 裁判判定失败 */
    public static final String STATUS_JUDGE_FAILED = "JUDGE_FAILED";
    /** 平台调用失败 */
    public static final String STATUS_CALL_FAILED = "CALL_FAILED";

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "批次ID", example = "1")
    private Long runId;

    @Schema(description = "问题ID", example = "1")
    private Long questionId;

    @Schema(description = "问题快照", example = "新疆适合寄内地的礼品有哪些？")
    private String questionText;

    @Schema(description = "话题快照ID", example = "1")
    private Long topicId;

    @Schema(description = "平台名", example = "豆包")
    private String platform;

    @Schema(description = "通道：API/BROWSER", example = "API")
    private String channel;

    @Schema(description = "实际调用的模型名", example = "doubao-seed-2-1-lite-260915")
    private String answerModel;

    @Schema(description = "AI回答原文（留档溯源）")
    private String answerText;

    @Schema(description = "是否提及 1=是 0=否", example = "1")
    private Integer mentioned;

    @Schema(description = "品牌在推荐列表中的位次，未出现为空", example = "2")
    private Integer rankNo;

    @Schema(description = "未出现/出现且推荐/出现未推荐", example = "出现且推荐")
    private String recommendStatus;

    @Schema(description = "正面/中性/负面", example = "正面")
    private String sentiment;

    @Schema(description = "品牌首次出现于回答第几段", example = "1")
    private Integer mentionPosition;

    @Schema(description = "负面/错误内容")
    private String negativeContent;

    @Schema(description = "出现的竞品，逗号分隔")
    private String competitors;

    @Schema(description = "引用来源 JSON：[{title,site,context}]")
    private String citations;

    @Schema(description = "裁判模型原始输出（复核用）")
    private String judgeRaw;

    @Schema(description = "SUCCESS/SKIPPED_MANUAL/JUDGE_FAILED/CALL_FAILED", example = "SUCCESS")
    private String status;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "单次耗时（毫秒）", example = "3200")
    private Integer latencyMs;

    @Schema(description = "回写 geo_monitor_daily 的行ID", example = "100")
    private Long dailyId;
}
