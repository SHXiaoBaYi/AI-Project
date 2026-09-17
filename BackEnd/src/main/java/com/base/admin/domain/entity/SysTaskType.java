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
@TableName("sys_task_type")
@Schema(description = "任务类型基础配置")
public class SysTaskType extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "类型名称", example = "文章发布")
    private String typeName;

    @Schema(description = "排序（越小越靠前）", example = "1")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "关联业务类型（如 geo_content_placement）", example = "geo_content_placement")
    private String bizType;

    @Schema(description = "分配回写业务字段编码（publisher/writer）", example = "writer")
    private String assignField;

    @Schema(description = "分配完成后派发的下一任务类型", example = "文章撰写")
    private String spawnTaskType;

    @Schema(description = "完成时是否必须上传证明附件 1=是 0=否", example = "1")
    private Integer requireProof;
}
