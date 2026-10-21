package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("geo_article_gen_trace")
@Schema(description = "AI成文流水线溯源")
public class GeoArticleGenTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long placementId;

    private Long itemId;

    private String styleCode;

    private String skeletonCode;

    private Integer qaRounds;

    private Integer usedImageCnt;

    private String stagesJson;

    private LocalDateTime createTime;
}
