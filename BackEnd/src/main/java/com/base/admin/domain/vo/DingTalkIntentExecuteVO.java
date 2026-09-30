package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "机器人 H5 意图执行结果")
public class DingTalkIntentExecuteVO {

    @Schema(description = "done=已完成；need_candidate=需选候选人")
    private String status;

    @Schema(description = "结果文案")
    private String message;

    @Schema(description = "待选候选人（status=need_candidate）")
    private List<Candidate> candidates = new ArrayList<>();

    @Data
    @Schema(description = "候选人选项")
    public static class Candidate {
        @Schema(description = "展示名")
        private String name;
        @Schema(description = "岗位")
        private String jobName;
        @Schema(description = "点选后继续执行的 invite 票据")
        private String ticket;
    }
}
