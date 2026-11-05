package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "阶段调整记录查询")
public class HrStageAdjustLogQueryDTO extends PageQuery {

    @Schema(description = "投递ID，不传则看权限范围内全部")
    private Long applicationId;

    @Schema(description = "候选人、岗位或操作人关键词")
    private String keyword;
}
