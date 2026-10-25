package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "培训文档拆出的问答")
public class TrainQaItemVO {

    @Schema(description = "题目ID", example = "1")
    private Long id;

    @Schema(description = "文档ID", example = "1")
    private Long docId;

    @Schema(description = "文档标题")
    private String docTitle;

    @Schema(description = "版本ID", example = "4")
    private Long versionId;

    @Schema(description = "版本序号", example = "2")
    private Integer versionNo;

    @Schema(description = "版本标签", example = "v2")
    private String versionLabel;

    @Schema(description = "该版本上传的文件名")
    private String fileName;

    @Schema(description = "所属收银文档文件ID", example = "3", nullable = true)
    private Long fileId;

    @Schema(description = "收银文档名称")
    private String fileTitle;

    @Schema(description = "是否为文档当前最新版")
    private Boolean latest;

    @Schema(description = "排序", example = "1")
    private Integer sortNo;

    @Schema(description = "题干")
    private String question;

    @Schema(description = "同义问法，一行一条")
    private String aliases;

    @Schema(description = "答文")
    private String answer;

    @Schema(description = "是否已写入向量")
    private Boolean embedded;
}
