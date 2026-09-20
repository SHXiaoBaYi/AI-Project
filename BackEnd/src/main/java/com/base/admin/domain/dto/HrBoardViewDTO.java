package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "保存看板视图")
public class HrBoardViewDTO {

    @NotBlank
    @Schema(description = "视图名称")
    private String viewName;

    @NotNull
    @Schema(description = "筛选条件")
    private HrBoardQueryDTO filter;

    @Schema(description = "是否默认")
    private Boolean isDefault;
}
