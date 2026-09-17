package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_task")
@Schema(description = "系统任务")
public class SysTask extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "任务说明")
    private String content;

    @Schema(description = "任务类型", example = "日常")
    private String taskType;

    @Schema(description = "优先级 1低2中3高4紧急", example = "2")
    private Integer priority;

    @Schema(description = "状态", example = "待处理")
    private String status;

    @Schema(description = "进度0-100", example = "0")
    private Integer progress;

    @Schema(description = "创建人用户ID")
    private Long creatorUserId;

    @Schema(description = "创建人展示名")
    private String creatorName;

    @Schema(description = "负责人用户ID")
    private Long ownerUserId;

    @Schema(description = "负责人展示名")
    private String ownerName;

    @Schema(description = "计划开始")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planStartTime;

    @Schema(description = "计划截止")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planEndTime;

    @Schema(description = "实际开始")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际完成")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    @Schema(description = "关联业务类型")
    private String bizType;

    @Schema(description = "关联业务ID")
    private Long bizId;

    @Schema(description = "关联业务摘要")
    private String bizTitle;

    @Schema(description = "父任务ID")
    private Long parentId;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
