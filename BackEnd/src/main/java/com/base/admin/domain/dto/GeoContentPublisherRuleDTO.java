package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "GEO文章发布人规则新增/修改")
public class GeoContentPublisherRuleDTO {

    @Schema(description = "规则ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotNull(message = "请选择发布人")
    @Schema(description = "发布人用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Long publisherUserId;

    @NotNull(message = "请选择是否默认")
    @Schema(description = "是否默认发布人 1=是 0=否", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer isDefault;

    @Schema(description = "备注")
    private String remark;
}
