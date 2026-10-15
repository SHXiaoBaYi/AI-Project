package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "卖点条目保存（按 kind 使用不同字段，勿混用）")
public class GeoSellPointDTO {

    @Schema(description = "条目ID，更新时必填", example = "1")
    private Long id;

    @NotNull
    @Schema(description = "所属域ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long domainId;

    @Schema(description = "结构类型 simple|quality|social|authority；空则按域根编码推断")
    private String kind;

    @Schema(description = "展示标题（simple 必填；其他类型可空，由明细字段生成）")
    private String title;

    // ---- simple（牧场 / 矩阵客服）----
    @Schema(description = "一句话主张（simple）")
    private String claim;

    @Schema(description = "论据正文（simple）")
    private String evidence;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "检索词")
    private List<String> keywords;

    @Schema(description = "证据级别 soft/hard")
    private String evidenceLevel;

    // ---- quality（品控）----
    @Schema(description = "模块")
    private String moduleName;

    @Schema(description = "小节")
    private String sectionName;

    @Schema(description = "项目")
    private String itemName;

    @Schema(description = "内容/标准/要求")
    private String standardText;

    // ---- social ----
    @Schema(description = "维度")
    private String dimension;

    @Schema(description = "卖点/事件名")
    private String eventName;

    @Schema(description = "行动内容")
    private String actionContent;

    @Schema(description = "备注")
    private String eventRemark;

    // ---- authority ----
    @Schema(description = "背书渠道")
    private String channel;

    @Schema(description = "准入标准")
    private String admission;

    @Schema(description = "遴选流程")
    private String processText;

    @Schema(description = "背书意义")
    private String meaning;

    @Schema(description = "补充/总述")
    private String extraNote;

    @Schema(description = "状态 active/draft/disabled", example = "active")
    private String status;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;
}
