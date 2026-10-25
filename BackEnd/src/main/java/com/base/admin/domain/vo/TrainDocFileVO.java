package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "版本下的收银文档文件")
public class TrainDocFileVO {

    @Schema(description = "文件ID", example = "1")
    private Long id;

    @Schema(description = "文档ID", example = "1")
    private Long docId;

    @Schema(description = "版本ID", example = "4")
    private Long versionId;

    @Schema(description = "版本标签", example = "v2")
    private String versionLabel;

    @Schema(description = "收银文档名称")
    private String title;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小")
    private Long fileSize;

    @Schema(description = "该文档下的题目数", example = "12")
    private Integer questionCount;
}
