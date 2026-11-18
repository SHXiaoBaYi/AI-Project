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
@TableName("geo_monitor_worker")
@Schema(description = "GEO自动监测-本机采集进程")
public class GeoMonitorWorkerNode extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "采集电脑名称，与平台账号 worker_node 一致", example = "office-pc-01")
    private String nodeName;

    @Schema(description = "最近心跳时间")
    private LocalDateTime lastSeen;

    @Schema(description = "当前正在做的队列ID")
    private Long currentJobId;

    @Schema(description = "当前正在使用的平台账号ID")
    private Long currentAccountId;

    @Schema(description = "worker 版本")
    private String version;

    @Schema(description = "备注")
    private String remark;
}
