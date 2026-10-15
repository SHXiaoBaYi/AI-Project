package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "卖点条目（含分类型明细字段）")
public class GeoSellPointVO {

    @Schema(description = "条目ID")
    private Long id;

    @Schema(description = "域ID")
    private Long domainId;

    @Schema(description = "域名称")
    private String domainName;

    @Schema(description = "结构类型 simple|quality|social|authority")
    private String kind;

    @Schema(description = "展示标题")
    private String title;

    @Schema(description = "一句话主张（simple / AI 摘要）")
    private String claim;

    @Schema(description = "论据正文（simple / AI 摘要）")
    private String evidence;

    @Schema(description = "标签")
    private List<String> tags = new ArrayList<>();

    @Schema(description = "检索词")
    private List<String> keywords = new ArrayList<>();

    @Schema(description = "证据级别")
    private String evidenceLevel;

    @Schema(description = "模块（quality）")
    private String moduleName;

    @Schema(description = "小节（quality）")
    private String sectionName;

    @Schema(description = "项目（quality）")
    private String itemName;

    @Schema(description = "内容/标准/要求（quality）")
    private String standardText;

    @Schema(description = "维度（social）")
    private String dimension;

    @Schema(description = "事件名（social）")
    private String eventName;

    @Schema(description = "行动内容（social）")
    private String actionContent;

    @Schema(description = "备注（social）")
    private String eventRemark;

    @Schema(description = "背书渠道（authority）")
    private String channel;

    @Schema(description = "准入标准（authority）")
    private String admission;

    @Schema(description = "遴选流程（authority）")
    private String processText;

    @Schema(description = "背书意义（authority）")
    private String meaning;

    @Schema(description = "补充/总述（authority）")
    private String extraNote;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "导入sheet")
    private String sourceSheet;

    @Schema(description = "导入行号")
    private Integer sourceRow;

    @Schema(description = "正文/论据字数")
    private Integer evidenceLen;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
