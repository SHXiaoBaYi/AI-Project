package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "本机领取到的一题")
public class GeoWorkerClaimVO {

    @Schema(description = "队列ID")
    private Long jobId;

    @Schema(description = "平台账号ID，对应该电脑上的 Chrome 用户目录")
    private Long accountId;

    @Schema(description = "账号脱敏标签")
    private String accountLabel;

    @Schema(description = "平台名")
    private String platform;

    @Schema(description = "要提问的原文")
    private String question;

    @Schema(description = "是否点开深度思考")
    private boolean deepThink;

    @Schema(description = "是否截整页图")
    private boolean screenshot;

    @Schema(description = "页面打开超时（毫秒）")
    private long navigationTimeoutMs;

    @Schema(description = "等待回答超时（毫秒）")
    private long answerTimeoutMs;

    @Schema(description = "页面选择器")
    private GeoWorkerProfileVO profile;
}
