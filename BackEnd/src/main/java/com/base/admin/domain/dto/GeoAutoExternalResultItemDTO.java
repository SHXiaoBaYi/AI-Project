package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 外网提交的单条监控结果。截图是图片 Base64，不落库。 */
@Data
@Schema(description = "外网提交的单条监控结果")
public class GeoAutoExternalResultItemDTO {

    @Schema(description = "问题原文", example = "全国羊肉哪里的最好")
    private String question;

    @Schema(description = "监控问题ID", example = "2")
    private Long questionId;

    @Schema(description = "平台名，须与平台管理里的名称一致", example = "豆包", requiredMode = Schema.RequiredMode.REQUIRED)
    private String platform;

    @Schema(description = "平台提问页", example = "https://www.doubao.com/chat/")
    private String platformUrl;

    @Schema(description = "回答页或官方分享链接", example = "https://www.doubao.com/chat/xxx")
    private String resultUrl;

    @Schema(description = "整页截图的 Base64，可带 data:image/png;base64, 前缀")
    private String screenshot;

    @Schema(description = "SUCCESS/CALL_FAILED/FAILED", example = "SUCCESS")
    private String status;

    @Schema(description = "回答正文，没有则留空")
    private String answer;

    @Schema(description = "采集完成时间", example = "2026-11-17 11:06:12")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectedAt;

    @Schema(description = "失败原因")
    private String errorMessage;
}
