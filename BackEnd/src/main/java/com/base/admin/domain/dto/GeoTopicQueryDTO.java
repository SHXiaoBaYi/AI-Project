package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO话题分页查询")
public class GeoTopicQueryDTO extends PageQuery {

    @Schema(description = "话题名称")
    private String topicName;
}
