package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "黑名单移入移出记录查询")
public class HrTalentBlacklistLogQueryDTO extends PageQuery {

    @Schema(description = "姓名、手机号或操作人")
    private String keyword;

    @Schema(description = "IN 移入 / OUT 移出，空为全部")
    private String action;
}
