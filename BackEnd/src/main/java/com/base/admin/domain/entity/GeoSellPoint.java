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
@TableName("geo_sell_point")
@Schema(description = "GEO卖点条目")
public class GeoSellPoint extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "条目ID", example = "1")
    private Long id;

    @Schema(description = "所属域ID", example = "1")
    private Long domainId;

    @Schema(description = "结构类型 simple|quality|social|authority", example = "quality")
    private String kind;

    @Schema(description = "短标题/展示名", example = "十年有机双认证")
    private String title;

    @Schema(description = "一句话主张", example = "连续十年获得有机产品与有机牧场认证")
    private String claim;

    @Schema(description = "论据正文")
    private String evidence;

    @Schema(description = "标签JSON", example = "[\"有机\",\"认证\"]")
    private String tagsJson;

    @Schema(description = "检索词JSON", example = "[\"有机\",\"认证\"]")
    private String keywordsJson;

    @Schema(description = "证据级别 soft/hard", example = "hard")
    private String evidenceLevel;

    @Schema(description = "导入sheet名")
    private String sourceSheet;

    @Schema(description = "导入行号", example = "4")
    private Integer sourceRow;

    @Schema(description = "状态 active/draft/disabled", example = "active")
    private String status;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "版本号", example = "1")
    private Integer versionNo;
}
