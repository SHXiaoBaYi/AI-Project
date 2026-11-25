package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "按手机号或邮箱查找已有人才")
public class HrTalentMatchQueryDTO {

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "排除的人才，编辑本人时使用")
    private Long excludeCandidateId;
}
