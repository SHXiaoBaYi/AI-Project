package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "院校")
public class HrSchoolVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "学校名称")
    private String name;

    @Schema(description = "英文名")
    private String nameEn;

    @Schema(description = "标识码")
    private String code;

    @Schema(description = "学校类型")
    private String schoolType;

    @Schema(description = "标签")
    private String tags;

    @Schema(description = "办学层次")
    private String eduLevel;

    @Schema(description = "所在地或国家地区")
    private String region;

    @Schema(description = "主管部门")
    private String authority;

    @Schema(description = "QS原文")
    private String qsRank;

    @Schema(description = "QS排名")
    private Integer rankNo;

    @Schema(description = "缩写")
    private String abbr;

    @Schema(description = "综合得分")
    private BigDecimal score;

    @Schema(description = "简介")
    private String intro;
}
