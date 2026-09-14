package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO平台分页查询")
public class GeoPlatformQueryDTO extends PageQuery {

    @Schema(description = "平台名称")
    private String platformName;
}
