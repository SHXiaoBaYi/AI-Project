package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "培训题目分页查询")
public class TrainQaItemQueryDTO extends PageQuery {

    @Schema(description = "分类", example = "cashier")
    private String category;

    @Schema(description = "文档ID", example = "1", nullable = true)
    private Long docId;

    @Schema(description = "版本ID。传入后只看该版本", example = "4", nullable = true)
    private Long versionId;

    @Schema(description = "收银文档文件ID。传入后只看这份文档的题目", example = "3", nullable = true)
    private Long fileId;

    @Schema(description = "仅看各文档最新版。指定版本时忽略", example = "true")
    private Boolean onlyLatest;

    @Schema(description = "关键词（题干/同义问法/答文）")
    private String keyword;
}
