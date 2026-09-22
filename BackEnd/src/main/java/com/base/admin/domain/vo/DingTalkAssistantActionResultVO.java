package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "日程助手动作执行结果")
public class DingTalkAssistantActionResultVO {

    @Schema(description = "助手日程记录ID", example = "1", nullable = true)
    private Long recordId;

    @Schema(description = "是否整体成功", example = "true")
    private boolean success;

    @Schema(description = "给前端展示的摘要")
    private String message;

    @Schema(description = "查询者日程 eventId", example = "evt-xxx", nullable = true)
    private String querierEventId;

    @Schema(description = "对方日程 eventId", example = "evt-yyy", nullable = true)
    private String targetEventId;

    @Schema(description = "系统任务ID（汇报时有）", example = "12", nullable = true)
    private Long taskId;

    @Schema(description = "是否已给查询者发工作通知", example = "true")
    private boolean noticeSent;
}
