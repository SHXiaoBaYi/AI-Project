package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "本机采集使用的平台页面选择器")
public class GeoWorkerProfileVO {

    @Schema(description = "平台名")
    private String name;

    @Schema(description = "提问页地址")
    private String url;

    @Schema(description = "输入框选择器")
    private String inputSelector;

    @Schema(description = "发送按钮选择器，空则回车发送")
    private String submitSelector;

    @Schema(description = "回答容器选择器")
    private String answerSelector;

    @Schema(description = "仍在生成的标志")
    private String streamingSelector;

    @Schema(description = "深度思考开关")
    private String deepThinkSelector;

    @Schema(description = "思考过程展开控件")
    private String reasoningSelector;

    @Schema(description = "思考过程文本容器")
    private String reasoningContentSelector;

    @Schema(description = "分享按钮")
    private String shareSelector;

    @Schema(description = "分享链接")
    private String shareLinkSelector;

    @Schema(description = "引用链接")
    private String citationSelector;

    @Schema(description = "出现即视为未登录")
    private List<String> loginRequiredSelectors = new ArrayList<>();

    @Schema(description = "提问前要点掉的弹窗")
    private List<String> dismissSelectors = new ArrayList<>();

    @Schema(description = "命中则不算真实回答")
    private List<String> answerRejectPatterns = new ArrayList<>();
}
