package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_sell_domain")
@Schema(description = "GEO卖点域")
public class GeoSellDomain extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "域ID", example = "1")
    private Long id;

    @Schema(description = "父域ID，0=根", example = "0")
    private Long parentId;

    @Schema(description = "域名称", example = "牧场与养殖方式")
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
