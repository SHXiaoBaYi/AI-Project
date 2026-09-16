package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "内容投放-发布人周看板")
public class GeoContentPublisherWeekBoardVO {

    @Schema(description = "查询周开始", example = "2026-09-08")
    private String startDate;

    @Schema(description = "查询周结束", example = "2026-09-14")
    private String endDate;

    @Schema(description = "发布人×周 汇总行")
    private List<GeoContentPublisherWeekRowVO> rows = new ArrayList<>();
}
