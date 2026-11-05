package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO自动监测-监控问题分页查询")
public class GeoAutoQuestionQueryDTO extends PageQuery {

    @Schema(description = "问题文本模糊搜索")
    private String questionText;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "1=启用 0=停用", example = "1")
    private Integer enabled;

    @Schema(description = "监测频率：每日/每周")
    private String freq;
}
