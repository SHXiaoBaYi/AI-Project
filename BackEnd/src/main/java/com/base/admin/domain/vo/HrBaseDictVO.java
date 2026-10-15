package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "招聘基础数据字典项")
public class HrBaseDictVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "字典类型 WORK_ADDRESS/BG_FAIL/OFFER_UNSENT/UNSUITABLE/JOB_PRIORITY", example = "JOB_PRIORITY")
    private String dictType;

    @Schema(description = "编码，岗位优先级为 P0/P1", example = "P0")
    private String code;

    @Schema(description = "地址类别，仅工作地址使用", example = "办公室")
    private String category;

    @Schema(description = "名称", example = "本月必须到岗")
    private String name;

    @Schema(description = "排序，越小越靠前", example = "1")
    private Integer sortNo;
}
