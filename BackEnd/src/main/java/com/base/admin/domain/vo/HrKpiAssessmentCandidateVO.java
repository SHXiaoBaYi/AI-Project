package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "考核任务达标候选人明细")
public class HrKpiAssessmentCandidateVO {

    @Schema(description = "需求岗位 ID")
    private Long requisitionId;

    @Schema(description = "岗位名称")
    private String jobName;

    @Schema(description = "核算月 yyyy-MM")
    private String settleMonth;

    @Schema(description = "完成方式 ONBOARD/QUALIFIED_RESUME")
    private String doneReason;

    @Schema(description = "达标门槛人数（入职人数或合格简历份数）", example = "3")
    private Integer target;

    @Schema(description = "核算月内命中人数", example = "4")
    private Integer hitCount;

    @Schema(description = "候选人列表（按命中时间升序）")
    private List<Item> items = new ArrayList<>();

    @Data
    @Schema(description = "候选人行")
    public static class Item {
        @Schema(description = "投递 ID")
        private Long applicationId;
        @Schema(description = "候选人 ID")
        private Long candidateId;
        @Schema(description = "姓名")
        private String candidateName;
        @Schema(description = "电话")
        private String phone;
        @Schema(description = "当前阶段")
        private String currentStage;
        @Schema(description = "简历提交人")
        private String submitterName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @Schema(description = "命中时间（入职时间 / 合格简历判定时间）")
        private LocalDateTime hitAt;
        @Schema(description = "是否属于凑够门槛、计入达标的那几位")
        private Boolean counted;
    }
}
