package com.base.admin.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "内容投放-发布详情新增/修改")
public class GeoContentPlacementItemDTO {

    @Schema(description = "明细ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotNull(message = "投放主表ID不能为空")
    @Schema(description = "投放主表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long placementId;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "标题（归属发布详情）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "发布平台不能为空")
    @Schema(description = "发布平台", requiredMode = Schema.RequiredMode.REQUIRED, example = "搜狐")
    private String platformName;

    @Schema(description = "内容形态（图文/视频）", example = "图文")
    private String contentForm;

    @NotBlank(message = "投放状态不能为空")
    @Schema(description = "投放状态（投放成功/审核未通过/未投放）", requiredMode = Schema.RequiredMode.REQUIRED, example = "投放成功")
    private String publishStatus;

    @Schema(description = "投放链接")
    private String publishUrl;

    @NotNull(message = "发布时间不能为空")
    @Schema(description = "发布时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-03 14:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
