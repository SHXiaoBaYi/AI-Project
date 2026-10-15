package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "卖点域保存")
public class GeoSellDomainDTO {

    @Schema(description = "域ID，更新时必填", example = "1")
    private Long id;

    @Schema(description = "父域ID，0=根", example = "0")
    private Long parentId;

    @NotBlank
    @Schema(description = "域名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "牧场与养殖方式")
    private String name;

    @Schema(description = "稳定编码", example = "ranch")
    private String code;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "1启用 0停用", example = "1")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;
}
