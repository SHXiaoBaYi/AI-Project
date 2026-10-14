package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "招聘基础数据字典项")
public class HrBaseDictDTO {

    @Schema(description = "主键，新增不传", example = "1", nullable = true)
    private Long id;

    @Schema(description = "编码，岗位优先级必填如 P0/P1", example = "P0")
    private String code;

    @Schema(description = "地址类别，工作地址必填", example = "办公室")
    private String category;

    @NotBlank(message = "名称不能为空")
    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "本月必须到岗")
    private String name;

    @Schema(description = "排序，越小越靠前", example = "1")
    private Integer sortNo;
}
