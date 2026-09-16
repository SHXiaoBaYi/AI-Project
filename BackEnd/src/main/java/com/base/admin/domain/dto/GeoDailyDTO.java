package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "GEO日监测新增/修改")
public class GeoDailyDTO {

    @Schema(description = "主键（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotNull(message = "巡查日期不能为空")
    @Schema(description = "巡查日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-01")
    private LocalDate inspectDate;

    @Schema(description = "长短词（日巡查/周巡查，默认日巡查）", example = "日巡查")
    private String termType;

    @NotBlank(message = "平台不能为空")
    @Schema(description = "平台", requiredMode = Schema.RequiredMode.REQUIRED, example = "豆包")
    private String platform;

    @NotBlank(message = "关键字不能为空")
    @Schema(description = "关键字/提问问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "新疆适合寄内地的礼品")
    private String keyword;

    @Schema(description = "负责人用户ID（关联 sys_user）", example = "1", nullable = true)
    private Long ownerUserId;

    @Schema(description = "负责人展示名（兼容旧数据；有 ownerUserId 时以后端解析为准）", example = "张三")
    private String ownerName;

    @NotNull(message = "话题不能为空")
    @Schema(description = "话题ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long topicId;

    @Schema(description = "是否露出（1=是 0=否）", example = "1")
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
}
