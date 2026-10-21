package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("geo_media_usage")
@Schema(description = "素材使用溯源")
public class GeoMediaUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;

    private Long placementId;

    private Long itemId;

    private String sectionKey;

    private LocalDateTime createTime;
}
