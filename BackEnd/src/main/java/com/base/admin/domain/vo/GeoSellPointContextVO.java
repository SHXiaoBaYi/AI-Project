package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "卖点召回上下文包（喂给 AI）")
public class GeoSellPointContextVO {

    @Schema(description = "话题")
    private String topic;

    @Schema(description = "目标问题")
    private String question;

    @Schema(description = "召回条目")
    private List<PointItem> points = new ArrayList<>();

    @Schema(description = "硬事实摘要")
    private List<String> hardFacts = new ArrayList<>();

    @Schema(description = "禁止编造提示")
    private List<String> forbiddenHints = new ArrayList<>();

    @Data
    @Schema(description = "召回条目明细")
    public static class PointItem {
        @Schema(description = "条目ID")
        private Long id;
        @Schema(description = "域名称")
        private String domain;
        @Schema(description = "标题")
        private String title;
        @Schema(description = "主张")
        private String claim;
        @Schema(description = "论据摘要")
        private String evidence;
        @Schema(description = "证据级别")
        private String level;
        @Schema(description = "标签")
        private List<String> tags = new ArrayList<>();
        @Schema(description = "召回分")
        private int score;
    }
}
