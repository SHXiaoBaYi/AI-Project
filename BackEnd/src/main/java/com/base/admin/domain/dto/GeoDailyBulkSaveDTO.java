package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "GEO日监测整单批量保存（多日期/多话题）")
public class GeoDailyBulkSaveDTO {

    @Schema(description = "是否忽略已统计冲突（true=跳过已统计记录继续保存其余）", example = "false")
    private Boolean ignoreLocked;

    @Valid
    @NotEmpty(message = "请至少提交一个话题分组")
    @Schema(description = "按日期+话题+关键字分组的监测数据")
    private List<GeoDailyBulkGroupDTO> groups;
}
