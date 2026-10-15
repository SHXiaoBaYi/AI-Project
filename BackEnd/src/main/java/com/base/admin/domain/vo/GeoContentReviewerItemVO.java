package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "审核人明细项")
public class GeoContentReviewerItemVO {

    @Schema(description = "用户ID", example = "3")
    private Long userId;

    @Schema(description = "姓名", example = "李四")
    private String userName;

    @Schema(description = "pending/approved/rejected", example = "pending")
    private String result;

    @Schema(description = "意见")
    private String comment;

    @Schema(description = "操作时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actedAt;
}
