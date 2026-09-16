package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "GEO日监测-单个平台指标")
public class GeoDailyPlatformItemDTO {

    @Schema(description = "记录ID（已有记录回传）", example = "1", nullable = true)
    private Long id;

    @NotBlank(message = "平台不能为空")
    @Schema(description = "平台名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "豆包")
    private String platform;

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
