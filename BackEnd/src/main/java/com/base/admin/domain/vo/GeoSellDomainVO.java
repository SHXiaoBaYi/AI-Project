package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "卖点域树节点")
public class GeoSellDomainVO {

    @Schema(description = "域ID")
    private Long id;

    @Schema(description = "父域ID")
    private Long parentId;

    @Schema(description = "域名称")
    private String name;

    @Schema(description = "编码")
    private String code;

    @Schema(description = "根域结构类型 simple|quality|social|authority")
    private String rootKind;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "启用")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "本域及子域下卖点条数（汇总）", example = "23")
    private Integer pointCount;

    @Schema(description = "子域")
    private List<GeoSellDomainVO> children = new ArrayList<>();
}
