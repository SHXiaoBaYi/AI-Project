package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "岗位候选人抽屉沟通统计（电话不通过 / 标记不合适）")
public class HrJobCommStatsVO {

    @Schema(description = "电话沟通不通过原因饼图")
    private List<PieSlice> phoneFailReasons = new ArrayList<>();

    @Schema(description = "标记不合适（初筛）原因饼图")
    private List<PieSlice> screenFailReasons = new ArrayList<>();

    @Schema(description = "沟通明细（电话不通过 + 标记不合适）")
    private List<Detail> details = new ArrayList<>();

    @Data
    @Schema(description = "饼图切片")
    public static class PieSlice {
        @Schema(description = "原因名")
        private String name;

        @Schema(description = "下钻键，通常等于原因名")
        private String key;

        @Schema(description = "人数", example = "3")
        private long value;
    }

    @Data
    @Schema(description = "沟通明细行")
    public static class Detail {
        @Schema(description = "投递ID", example = "21")
        private Long applicationId;

        @Schema(description = "候选人姓名")
        private String displayName;

        @Schema(description = "记录类型 PHONE_FAIL / SCREEN_FAIL")
        private String recordType;

        @Schema(description = "记录类型文案")
        private String recordTypeLabel;

        @Schema(description = "结果 PASS/FAIL")
        private String result;

        @Schema(description = "原因")
        private String reason;

        @Schema(description = "备注")
        private String remark;

        @Schema(description = "发生时间 yyyy-MM-dd HH:mm:ss")
        private String occurredAt;

        @Schema(description = "当前阶段码")
        private String currentStage;

        @Schema(description = "当前阶段名")
        private String stageName;

        @Schema(description = "简历文件名")
        private String resumeName;
    }
}
