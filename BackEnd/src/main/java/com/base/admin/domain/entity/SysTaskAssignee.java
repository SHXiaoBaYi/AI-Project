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
@TableName("sys_task_assignee")
@Schema(description = "任务执行人")
public class SysTaskAssignee extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "执行人用户ID")
    private Long userId;

    @Schema(description = "执行人展示名")
    private String userName;

    @Schema(description = "角色：执行/协作/抄送", example = "执行")
    private String roleLabel;

    @Schema(description = "个人是否完成 1=是 0=否", example = "0")
    private Integer done;
}
