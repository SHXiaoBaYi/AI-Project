package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "秘书推荐可约时段结果")
public class DingTalkScheduleRecommendVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户展示名")
    private String displayName;

    @Schema(description = "动作")
    private String action;

    @Schema(description = "推荐时长分钟")
    private int durationMin;

    @Schema(description = "说明")
    private String message;

    @Schema(description = "推荐时段（按时间升序）")
    private List<Slot> slots = new ArrayList<>();

    @Data
    @Schema(description = "推荐时段")
    public static class Slot {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @Schema(description = "开始")
        private LocalDateTime start;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @Schema(description = "结束")
        private LocalDateTime end;

        @Schema(description = "展示文案")
        private String label;
    }
}
