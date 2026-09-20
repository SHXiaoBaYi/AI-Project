package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "院校查询")
public class HrSchoolQueryDTO {

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "DOMESTIC 国内院校，QS 海外院校")
    private String kind;

    @Schema(description = "学校名称")
    private String name;

    @Schema(description = "所在地或国家地区")
    private String region;

    @Schema(description = "院校标签，仅国内")
    private String tags;
}
