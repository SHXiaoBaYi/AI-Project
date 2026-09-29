package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "电商归档本周请求")
public class EcomArchiveWeekDTO {

    @Schema(description = "平台", example = "jd")
    private String platform = "jd";

    @Schema(description = "店铺ID，空=该平台下全部店铺")
    private Long shopId;

    @Schema(description = "周内任意一天，空=本周", example = "2026-09-22")
    private String anchorDate;
}
