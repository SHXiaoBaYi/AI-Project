package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "答疑结果")
public class TrainAssistantAskVO {

    @Schema(description = "HIT / NEED_OLD / NOT_FOUND")
    private String status;

    @Schema(description = "提示文案")
    private String message;

    @Schema(description = "命中的简易问答")
    private List<QaItem> items = new ArrayList<>();

    @Schema(description = "检索的版本标签")
    private String versionLabel;

    @Schema(description = "是否还有更旧版本可检索")
    private Boolean hasOlderVersions;

    @Data
    @Schema(description = "问答条目")
    public static class QaItem {
        @Schema(description = "问法")
        private String question;

        @Schema(description = "答法（纯文本）")
        private String answer;

        @Schema(description = "答法 HTML（含图片，优先展示）")
        private String answerHtml;

        @Schema(description = "相关配图路径（/uploads/...）")
        private List<String> images = new ArrayList<>();

        @Schema(description = "来源版本标签")
        private String versionLabel;
    }
}
