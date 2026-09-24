package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO 话题×关键字×平台 日期明细与折线序列")
public class GeoDailyComboDetailVO {

    @Schema(description = "组合摘要")
    private GeoDailyComboVO summary;

    @Schema(description = "日期明细（按巡查日升序；同日多条话题类型已合并）")
    private List<GeoDailyVO> days = new ArrayList<>();

    @Schema(description = "折线点（与 days 一一对应，便于前端直接绑图）")
    private List<GeoDailyComboPointVO> series = new ArrayList<>();
}
