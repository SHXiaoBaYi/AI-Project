package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "用户导入结果")
public class UserImportResultVO {

    @Schema(description = "总条数", example = "10")
    private int totalCount;

    @Schema(description = "成功条数", example = "8")
    private int successCount;

    @Schema(description = "失败条数", example = "2")
    private int failureCount;

    @Schema(description = "错误详情列表")
    private List<UserImportErrorVO> errors = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @Schema(description = "导入错误明细")
    public static class UserImportErrorVO {

        @Schema(description = "行号", example = "3")
        private int rowIndex;

        @Schema(description = "出错字段", example = "username")
        private String field;

        @Schema(description = "错误信息", example = "用户名已存在")
        private String message;
    }
}
