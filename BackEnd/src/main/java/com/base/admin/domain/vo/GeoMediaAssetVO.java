package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "GEO素材展示")
public class GeoMediaAssetVO {

    private Long id;
    private String title;
    private String fileUrl;
    private String publicUrl;
    private String thumbUrl;
    private String sourceType;
    private String licenseNote;
    private Integer brandVisible;
    private String caption;
    private String altText;
    private List<String> tags;
    private String usageHint;
    private List<String> sceneTags;
    private Integer widthPx;
    private Integer heightPx;
    private String status;
    private String remark;
    private List<Long> topicIds;
    private List<String> topicNames;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
