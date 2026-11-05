package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "GEO自动监测-浏览器单题采集试跑结果（含取证与诊断）")
public class GeoBrowserProbeVO {

    @Schema(description = "是否采集成功")
    private boolean success;

    @Schema(description = "失败原因")
    private String error;

    @Schema(description = "平台")
    private String platform;

    @Schema(description = "监控问题")
    private String question;

    @Schema(description = "AI 回答原文")
    private String answerText;

    @Schema(description = "模型思考过程")
    private String reasoningText;

    @Schema(description = "C 端官方分享链接")
    private String probeUrl;

    @Schema(description = "链接来源：OFFICIAL_SHARE/PAGE_URL")
    private String probeUrlSource;

    @Schema(description = "整页截图地址")
    private String screenshotUrl;

    @Schema(description = "截图类型")
    private String screenshotType;

    @Schema(description = "当前会话页面地址")
    private String pageUrl;

    @Schema(description = "模型标签")
    private String modelLabel;

    @Schema(description = "深度思考是否实际开启")
    private boolean deepThinkApplied;

    @Schema(description = "引用信源（页面 DOM 提取，带真实链接）")
    private List<GeoAutoResultDetailVO.Citation> citations;

    @Schema(description = "采集诊断 JSON 字符串")
    private String diagnostic;
}
