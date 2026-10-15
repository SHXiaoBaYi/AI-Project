package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "答疑未命中提问分页查询")
public class TrainQaMissQueryDTO extends PageQuery {

    @Schema(description = "分类", example = "cashier")
    private String category;

    @Schema(description = "关键词（问法/提问人）", example = "折扣")
    private String keyword;
}
