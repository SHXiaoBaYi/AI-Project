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
@TableName("sys_task_file")
@Schema(description = "任务附件/完成证明")
public class SysTaskFile extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "关联业务类型")
    private String bizType;

    @Schema(description = "关联业务ID")
    private Long bizId;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "访问路径")
    private String fileUrl;

    @Schema(description = "字节大小")
    private Long fileSize;

    @Schema(description = "MIME")
    private String contentType;

    @Schema(description = "上传人ID")
    private Long uploadUserId;

    @Schema(description = "上传人")
    private String uploadUserName;

    @Schema(description = "备注")
    private String remark;
}
