package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO自动监测-结果详情（含回答原文与昨日对比）")
public class GeoAutoResultDetailVO extends GeoAutoResultVO {

    @Schema(description = "AI回答原文（留档溯源）")
    private String answerText;

    @Schema(description = "模型思考过程（reasoning_content，模型未返回则为空）")
    private String reasoningText;

    @Schema(description = "探测链接：C端官方分享链接（点击直达原始回答页）")
    private String probeUrl;

    @Schema(description = "链接来源：OFFICIAL_SHARE=官方分享链接 / PAGE_URL=会话页面地址")
    private String probeUrlSource;

    @Schema(description = "浏览器采集诊断JSON字符串（目标URL/步骤耗时/选择器命中/UA，不含登录态）")
    private String probeRequest;

    @Schema(description = "探测截图地址")
    private String screenshotUrl;

    @Schema(description = "截图类型：MANUAL/API_SNAPSHOT/BROWSER_SHOT")
    private String screenshotType;

    @Schema(description = "引用来源 JSON 数组：[{title,site,url,context}]")
    private List<Citation> citations;

    @Schema(description = "裁判模型原始输出（复核用）")
    private String judgeRaw;

    @Schema(description = "对比项：与上一次监测的变化列表")
    private List<DiffItem> diffs;

    @Data
    @Schema(description = "引用来源")
    public static class Citation {

        @Schema(description = "来源标题")
        private String title;

        @Schema(description = "来源站点")
        private String site;

        @Schema(description = "信源链接（http/https）")
        private String url;

        @Schema(description = "引用上下文")
        private String context;
    }

    @Data
    @Schema(description = "与上次监测的差异项")
    public static class DiffItem {

        @Schema(description = "指标名：mentioned/rankNo/sentiment/recommendStatus/competitors", example = "rankNo")
        private String field;

        @Schema(description = "指标中文名", example = "排名")
        private String label;

        @Schema(description = "上次值", example = "3")
        private String before;

        @Schema(description = "本次值", example = "1")
        private String after;

        @Schema(description = "变化方向：up/down/flat", example = "up")
        private String direction;
    }
}
