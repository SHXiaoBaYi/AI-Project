package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "任务附件")
public class SysTaskFileVO {

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

    @Schema(description = "访问路径（可下载）")
    private String fileUrl;

    @Schema(description = "字节大小")
    private Long fileSize;

    @Schema(description = "MIME")
    private String contentType;

    @Schema(description = "上传人")
    private String uploadUserName;

    @Schema(description = "上传时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
