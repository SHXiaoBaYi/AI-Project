package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "收银答疑长期扫码入口")
public class TrainAssistantEntryVO {

    @Schema(description = "长期有效的 H5 页面地址（二维码内容优先用它）")
    private String pageUrl;

    @Schema(description = "钉钉内打开协议链接（可复制到工作通知等）")
    private String dingTalkUrl;

    @Schema(description = "企业 CorpId")
    private String corpId;

    @Schema(description = "是否已配置完整（可生成入口）")
    private Boolean ready;

    @Schema(description = "不可用时的原因")
    private String message;
}
