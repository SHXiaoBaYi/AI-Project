package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 把平台账号加入或移出浏览器监测候选池。 */
@Data
@Schema(description = "浏览器监测池开关")
public class GeoBrowserPoolToggleDTO {

    @NotNull(message = "请指定是否加入监测池")
    @Schema(description = "1=加入监测池 0=移出", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer enabled;
}
