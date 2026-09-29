package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "电商目标保存")
public class EcomTargetSaveDTO {

    @Schema(description = "平台 all/jd/tmall/douyin", example = "jd")
    private String platform = "jd";

    @Schema(description = "店铺ID，0=平台级")
    private Long shopId = 0L;

    @NotBlank
    @Schema(description = "week/month/year")
    private String periodType;

    @NotBlank
    @Schema(description = "周期键，如 2026-W39 / 2026-09 / 2026")
    private String periodKey;

    @Schema(description = "展示名")
    private String periodLabel;

    @Schema(description = "成交金额目标")
    private BigDecimal targetGmv;

    @Schema(description = "成交单量目标")
    private BigDecimal targetOrder;

    @Schema(description = "备注")
    private String remark;
}
