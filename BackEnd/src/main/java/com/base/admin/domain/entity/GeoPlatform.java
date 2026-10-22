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
@TableName("geo_platform")
@Schema(description = "GEO监测平台")
public class GeoPlatform extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "平台ID", example = "1")
    private Long id;

    @Schema(description = "平台名称", example = "豆包")
    private String platformName;

    @Schema(description = "平台类型（AI平台/内容发布平台）", example = "AI平台")
    private String platformType;

    @Schema(description = "平台登录地址", example = "https://www.douyin.com")
    private String loginUrl;

    @Schema(description = "官方内容审核规范链接（多条换行）")
    private String contentAuditUrls;

    @Schema(description = "官方内容审核规范摘要/补充说明（成文约束）")
    private String contentAuditNotes;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
