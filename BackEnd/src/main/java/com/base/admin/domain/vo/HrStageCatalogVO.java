package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "招聘候选人阶段中央目录（展示名 / 流水线大段 / 漏斗 bucket）")
public class HrStageCatalogVO {

    @Schema(description = "细粒度阶段（来自 hr_stage_def）")
    private List<StageItem> stages = new ArrayList<>();

    @Schema(description = "流水线四列")
    private List<PhaseItem> phases = new ArrayList<>();

    @Schema(description = "看板漏斗 bucket 序列")
    private List<FunnelItem> funnel = new ArrayList<>();

    @Schema(description = "code → 展示名兜底（库无记录时用）")
    private Map<String, String> aliases = new LinkedHashMap<>();

    @Data
    @Schema(description = "阶段项")
    public static class StageItem {
        @Schema(description = "阶段码", example = "FIRST_ROUND")
        private String code;

        @Schema(description = "展示名", example = "一面通过")
        private String name;

        @Schema(description = "排序", example = "60")
        private Integer sortNo;

        @Schema(description = "是否终态", example = "false")
        private Boolean terminal;

        @Schema(description = "流水线大段 SCREEN/PHONE/INTERVIEW/ONBOARD", example = "INTERVIEW")
        private String phase;

        @Schema(description = "所属漏斗 bucket，可空", example = "FIRST")
        private String funnelBucket;
    }

    @Data
    @Schema(description = "流水线大段")
    public static class PhaseItem {
        @Schema(description = "大段码", example = "SCREEN")
        private String code;

        @Schema(description = "展示名", example = "简历筛选")
        private String name;

        @Schema(description = "顺序，从 0 起", example = "0")
        private Integer order;
    }

    @Data
    @Schema(description = "漏斗 bucket")
    public static class FunnelItem {
        @Schema(description = "bucket 码", example = "PHONE")
        private String code;

        @Schema(description = "展示名", example = "电话通过")
        private String name;

        @Schema(description = "顺序，从 0 起", example = "1")
        private Integer order;

        @Schema(description = "转化率文案", example = "相对上一层")
        private String conversionLabel;

        @Schema(description = "落入该层的 current_stage 集合")
        private List<String> memberStageCodes = new ArrayList<>();
    }
}
