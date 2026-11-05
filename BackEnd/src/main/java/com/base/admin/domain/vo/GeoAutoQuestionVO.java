package com.base.admin.domain.vo;

import com.base.admin.domain.entity.GeoMonitorQuestion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO自动监测-监控问题（含话题名）")
public class GeoAutoQuestionVO extends GeoMonitorQuestion {

    @Schema(description = "话题名称", example = "送礼")
    private String topicName;
}
