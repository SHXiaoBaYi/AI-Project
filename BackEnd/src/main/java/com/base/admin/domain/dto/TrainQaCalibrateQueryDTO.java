package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "校准问答分页查询")
public class TrainQaCalibrateQueryDTO extends PageQuery {

    @Schema(description = "分类", example = "cashier")
    private String category;

    @Schema(description = "文档ID", example = "1", nullable = true)
    private Long docId;

    @Schema(description = "状态 APPROVED/REJECTED/PENDING/STALE", example = "APPROVED")
    private String status;

    @Schema(description = "关键词（问法/话题/答案）", example = "寄存")
    private String keyword;

    @Schema(description = "是否仅看当前最新版", example = "true")
    private Boolean onlyLatest;

    @Schema(description = "是否已处理（true=已处理 false=未处理）", example = "false", nullable = true)
    private Boolean handled;

    @Schema(description = "处理状态 OPEN/INTENT_BAD/IGNORE/DOC_MISSING/DOC_DONE/AI_INTENT_DONE", example = "OPEN", nullable = true)
    private String handleStatus;
}
