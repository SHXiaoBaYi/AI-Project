package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "GEO日监测详情")
public class GeoDailyVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "巡查日期", example = "2026-09-01")
    private LocalDate inspectDate;

    @Schema(description = "平台", example = "豆包")
    private String platform;

    @Schema(description = "关键字", example = "新疆适合寄内地的礼品")
    private String keyword;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "话题名称", example = "送礼")
    private String topicName;

    @Schema(description = "是否提及（1=是 0=否）", example = "1")
    private Integer mentioned;

    @Schema(description = "排名", example = "1", nullable = true)
    private Integer rankNo;

    @Schema(description = "推荐状态", example = "出现且推荐")
    private String recommendStatus;

    @Schema(description = "截图URL")
    private String screenshotUrl;

    @Schema(description = "第三方链接")
    private String thirdPartyUrl;

    @Schema(description = "负面/错误内容")
    private String negativeContent;

    @Schema(description = "出现的竞品")
    private String competitors;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
