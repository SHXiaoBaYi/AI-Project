package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "平台文章内部审核意见")
public class GeoContentOpinionVO {

    @Schema(description = "意见ID", example = "1")
    private Long id;

    @Schema(description = "平台文章ID", example = "1")
    private Long itemId;

    @Schema(description = "意见内容")
    private String opinion;

    @Schema(description = "意见人", example = "李金瑜")
    private String authorName;

    @Schema(description = "时间", example = "2026-10-12 15:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
