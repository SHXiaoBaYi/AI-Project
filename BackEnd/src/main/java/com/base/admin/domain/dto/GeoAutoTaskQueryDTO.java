package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO自动监测-任务分页查询")
public class GeoAutoTaskQueryDTO extends PageQuery {

    @Schema(description = "任务名模糊搜索")
    private String taskName;

    @Schema(description = "1=启用 0=停用", example = "1")
    private Integer enabled;
}
