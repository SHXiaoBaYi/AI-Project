package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "黑名单人才查询")
public class HrTalentBlacklistQueryDTO {

    @Schema(description = "姓名、手机号或邮箱")
    private String keyword;
}
