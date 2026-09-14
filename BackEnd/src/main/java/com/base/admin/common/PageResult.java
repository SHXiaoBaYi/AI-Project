package com.base.admin.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "分页响应结果")
public class PageResult<T> {

    @Schema(description = "总条数", example = "100")
    private long total;

    @Schema(description = "数据列表")
    private List<T> rows;

    public PageResult(long total, List<T> rows) {
        this.total = total;
        this.rows = rows;
    }
}
