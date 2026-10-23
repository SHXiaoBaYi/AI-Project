package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "审稿知识库-门店结构化字段")
public class GeoReviewKbStoreAttrs {

    @Schema(description = "营业时间", example = "09:00-21:00")
    private String openHours;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "座机")
    private String landline;

    @Schema(description = "手机")
    private String mobile;

    @Schema(description = "覆盖范围/所属区域", example = "沙依巴克区")
    private String coverage;
}
