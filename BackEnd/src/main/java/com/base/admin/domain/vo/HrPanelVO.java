package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "招聘面板")
public class HrPanelVO {

    @Schema(description = "统计数字")
    private HrPanelStatsVO stats;

    @Schema(description = "总条数", example = "20")
    private Long total;

    @Schema(description = "岗位列表")
    private List<HrPanelJobVO> rows;
}
