package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "按版本重新拆题结果")
public class TrainQaRebuildVO {

    @Schema(description = "版本ID")
    private Long versionId;

    @Schema(description = "各文档拆题结果")
    private List<FileResult> files = new ArrayList<>();

    @Data
    @Schema(description = "单份文档拆题结果")
    public static class FileResult {

        @Schema(description = "文件ID")
        private Long fileId;

        @Schema(description = "文档名称")
        private String fileName;

        @Schema(description = "拆出的题目数")
        private Integer questionCount;

        @Schema(description = "失败原因，成功时为空")
        private String error;
    }
}
