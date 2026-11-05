package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_monitor_run")
@Schema(description = "GEO自动监测-运行批次")
public class GeoMonitorRun extends BaseEntity {

    /** 运行中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 全部成功（含人工优先跳过） */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 部分失败 */
    public static final String STATUS_PARTIAL = "PARTIAL";
    /** 全部失败 / 批次异常 */
    public static final String STATUS_FAILED = "FAILED";

    /** 定时触发 */
    public static final String TRIGGER_SCHEDULED = "SCHEDULED";
    /** 手动触发 */
    public static final String TRIGGER_MANUAL = "MANUAL";

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "任务ID", example = "1")
    private Long taskId;

    @Schema(description = "任务名快照", example = "核心问题每日监测")
    private String taskName;

    @Schema(description = "监测归属日期（回写日监测的inspect_date）", example = "2026-11-05")
    private LocalDate runDate;

    @Schema(description = "触发方式：SCHEDULED/MANUAL", example = "MANUAL")
    private String triggerType;

    @Schema(description = "状态：RUNNING/SUCCESS/PARTIAL/FAILED", example = "RUNNING")
    private String status;

    @Schema(description = "问题×平台总数", example = "40")
    private Integer totalCount;

    @Schema(description = "已处理数", example = "40")
    private Integer doneCount;

    @Schema(description = "成功数（含SKIPPED_MANUAL）", example = "38")
    private Integer successCount;

    @Schema(description = "失败数（调用失败/判定失败）", example = "2")
    private Integer failedCount;

    /** 分批串行：每批只跑 batchSize 个问题，跑完一批再起下一批，避免服务器持续满负荷 */
    @Schema(description = "每批监测问题数", example = "3")
    private Integer batchSize;

    @Schema(description = "总批次数", example = "5")
    private Integer batchTotal;

    @Schema(description = "已完成批次数", example = "2")
    private Integer batchDone;

    @Schema(description = "当前执行批次号（从1开始）", example = "3")
    private Integer currentBatchNo;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "批次级错误摘要")
    private String errorMsg;
}
