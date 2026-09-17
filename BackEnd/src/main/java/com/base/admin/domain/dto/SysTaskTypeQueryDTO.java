package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务类型分页查询")
public class SysTaskTypeQueryDTO extends PageQuery {

    @Schema(description = "类型名称（模糊）")
    private String typeName;
}
