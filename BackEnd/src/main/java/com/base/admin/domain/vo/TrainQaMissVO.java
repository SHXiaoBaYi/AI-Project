package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "答疑未命中提问")
public class TrainQaMissVO {

    @Schema(description = "ID", example = "1")
    private Long id;

    @Schema(description = "分类", example = "cashier")
    private String category;

    @Schema(description = "用户原问")
    private String question;

    @Schema(description = "分词意图")
    private String intentTokens;

    @Schema(description = "用户ID", example = "12")
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "提问人姓名")
    private String nickname;

    @Schema(description = "状态 NEED_OLD/NOT_FOUND", example = "NOT_FOUND")
    private String status;

    @Schema(description = "文档版本ID", example = "4", nullable = true)
    private Long versionId;

    @Schema(description = "是否已处理", example = "false")
    private Boolean handled;

    @Schema(description = "处理状态 OPEN/INTENT_BAD/IGNORE/DOC_MISSING", example = "OPEN")
    private String handleStatus;

    @Schema(description = "处理人")
    private String handleBy;

    @Schema(description = "处理时间")
    private String handleTime;

    @Schema(description = "提问时间")
    private String createTime;
}
