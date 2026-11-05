package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_monitor_task")
@Schema(description = "GEO自动监测-任务配置")
public class GeoMonitorTask extends BaseEntity {

    /** 圈选方式：按话题 */
    public static final String SCOPE_TOPIC = "TOPIC";
    /** 圈选方式：按问题 */
    public static final String SCOPE_QUESTION = "QUESTION";

    /** 频率：每日 */
    public static final String FREQ_DAILY = "每日";
    /** 频率：每周 */
    public static final String FREQ_WEEKLY = "每周";

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "任务名", example = "核心问题每日监测")
    private String taskName;

    @Schema(description = "圈选方式：TOPIC=按话题 / QUESTION=按问题", example = "TOPIC")
    private String scopeType;

    @Schema(description = "圈选ID列表，逗号分隔", example = "1,2,3")
    private String scopeIds;

    @Schema(description = "监测平台名列表，逗号分隔", example = "豆包,DS")
    private String platforms;

    @Schema(description = "每日/每周", example = "每日")
    private String freq;

    @Schema(description = "每日运行时刻", example = "08:30:00")
    private LocalTime runTime;

    @Schema(description = "每周模式：1=周一 … 7=周日", example = "1")
    private Integer weekDay;

    @Schema(description = "同日同题同平台有人工记录时是否覆盖 0=人工优先", example = "0")
    private Integer overwriteManual;

    @Schema(description = "变化提醒开关（P2 生效）", example = "1")
    private Integer notifyEnabled;

    @Schema(description = "1=启用 0=停用", example = "1")
    private Integer enabled;

    @Schema(description = "最近一次运行批次ID", example = "1")
    private Long lastRunId;

    @Schema(description = "下次运行时间")
    private LocalDateTime nextRunTime;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
