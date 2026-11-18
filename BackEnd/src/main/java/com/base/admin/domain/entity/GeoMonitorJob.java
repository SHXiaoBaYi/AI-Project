package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_monitor_job")
@Schema(description = "GEO自动监测-本机采集队列")
public class GeoMonitorJob extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_LEASED = "LEASED";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "运行批次ID")
    private Long runId;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "问题ID")
    private Long questionId;

    @Schema(description = "问题快照")
    private String questionText;

    @Schema(description = "平台ID")
    private Long platformId;

    @Schema(description = "平台名")
    private String platformName;

    @Schema(description = "领取后绑定的平台账号ID")
    private Long accountId;

    @Schema(description = "PENDING/LEASED/DONE/FAILED")
    private String status;

    @Schema(description = "领取该题的采集电脑")
    private String leaseOwner;

    @Schema(description = "领取到期时间，过期后可被重新领取")
    private LocalDateTime leaseUntil;

    @Schema(description = "已失败次数")
    private Integer attempt;

    @Schema(description = "最近一次失败或等待原因")
    private String errorMsg;

    @Schema(description = "落库后的结果ID")
    private Long resultId;

    @Schema(description = "是否开启深度思考 1=是")
    private Integer deepThink;

    @Schema(description = "截图模式 DISABLED/MENTION/ALL")
    private String screenshotMode;

    @Schema(description = "提问位置")
    private String probeRegion;
}
