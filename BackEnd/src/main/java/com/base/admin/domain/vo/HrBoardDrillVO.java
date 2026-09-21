package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "招聘看板下钻")
public class HrBoardDrillVO {

    @Schema(description = "STAGE/HC/INTERVIEW/INTERVIEWER")
    private String kind;

    @Schema(description = "漏斗/面试指标下的候选人")
    private List<HrBoardVO.DrillRow> candidates = new ArrayList<>();

    @Schema(description = "招聘完成指标下的需求")
    private List<HrBoardVO.ProgressRow> requisitions = new ArrayList<>();

    @Schema(description = "面试官维度下的面试记录")
    private List<HrBoardVO.InterviewRecordRow> interviews = new ArrayList<>();
}
