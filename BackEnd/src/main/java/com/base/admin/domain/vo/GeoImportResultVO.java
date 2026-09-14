package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO导入结果")
public class GeoImportResultVO {

    @Schema(description = "总条数", example = "10")
    private int totalCount;

    @Schema(description = "新增条数", example = "6")
    private int insertCount;

    @Schema(description = "更新条数", example = "3")
    private int updateCount;

    @Schema(description = "失败条数", example = "1")
    private int failureCount;

    @Schema(description = "错误详情")
    private List<GeoImportErrorVO> errors = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "导入错误")
    public static class GeoImportErrorVO {
        @Schema(description = "行号", example = "4")
        private int rowIndex;

        @Schema(description = "字段")
        private String field;

        @Schema(description = "错误信息")
        private String message;
    }
}
