package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "招聘面板指标明细")
public class HrPanelDetailVO {

    @Schema(description = "指标编码", example = "TODAY_SCREEN")
    private String metric;

    @Schema(description = "标题", example = "今日筛选简历")
    private String title;

    @Schema(description = "JOB岗位 PERSON人员", example = "PERSON")
    private String kind;

    @Schema(description = "总条数", example = "6")
    private Long total;

    @Schema(description = "岗位明细，kind=JOB")
    private List<HrPanelJobVO> jobs = new ArrayList<>();

    @Schema(description = "人员明细，kind=PERSON")
    private List<HrPanelPersonVO> people = new ArrayList<>();
}
