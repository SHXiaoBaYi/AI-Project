package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "GEO 组合日期折线点")
public class GeoDailyComboPointVO {

    @Schema(description = "巡查日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate inspectDate;

    @Schema(description = "日期标签", example = "09-01")
    private String dateLabel;

    @Schema(description = "是否露出 1/0", example = "1")
    private Integer mentioned;

    @Schema(description = "排名", example = "3", nullable = true)
    private Integer rankNo;

    @Schema(description = "是否有负面 1/0", example = "0")
    private Integer hasNegative;

    @Schema(description = "推荐状态")
    private String recommendStatus;
}
