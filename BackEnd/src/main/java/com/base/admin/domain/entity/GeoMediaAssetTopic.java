package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("geo_media_asset_topic")
@Schema(description = "素材-话题关联")
public class GeoMediaAssetTopic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;

    private Long topicId;

    private LocalDateTime createTime;
}
