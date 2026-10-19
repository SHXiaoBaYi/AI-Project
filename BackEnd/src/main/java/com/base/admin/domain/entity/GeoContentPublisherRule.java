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
@TableName("geo_content_publisher_rule")
@Schema(description = "GEO文章发布人规则")
public class GeoContentPublisherRule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "规则ID", example = "1")
    private Long id;

    @Schema(description = "发布人用户ID", example = "3")
    private Long publisherUserId;

    @Schema(description = "发布人展示名", example = "小穆")
    private String publisherName;

    @Schema(description = "是否默认发布人 1=是 0=否（未指定发布人时自动套用）", example = "1")
    private Integer isDefault;

    @Schema(description = "备注")
    private String remark;
}
