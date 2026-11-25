package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "人才保存结果")
public class HrTalentSaveResultVO {

    @Schema(description = "人才ID")
    private Long candidateId;

    @Schema(description = "用于挂简历的投递ID，可能为空")
    private Long applicationId;

    @Schema(description = "本次是否合并到已有人才")
    private Boolean merged;
}
