package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "候选人针对招聘需求的一份 AI 分析")
public class HrApplicationAiVO {

    @Schema(description = "分析ID")
    private Long id;

    @Schema(description = "投递ID")
    private Long applicationId;

    @Schema(description = "招聘需求ID")
    private Long requisitionId;

    @Schema(description = "厂商标识")
    private String provider;

    @Schema(description = "模型展示名")
    private String providerName;

    @Schema(description = "模型名")
    private String modelName;

    @Schema(description = "打分 0-100")
    private Integer score;

    @Schema(description = "优劣势")
    private String prosCons;

    @Schema(description = "面试建议")
    private String interviewAdvice;

    @Schema(description = "生成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
