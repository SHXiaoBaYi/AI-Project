package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "把人才推荐到岗位，生成一条新投递")
public class HrTalentRecommendDTO {

    @NotNull
    @Schema(description = "人才ID")
    private Long candidateId;

    @NotNull
    @Schema(description = "招聘需求")
    private Long requisitionId;

    @Schema(description = "渠道，空则沿用未挂岗投递上的渠道")
    private String channelCode;

    @Schema(description = "简历提交人用户")
    private Long submitterUserId;

    @Schema(description = "推荐日期，空则今天")
    private LocalDate submittedAt;
}
