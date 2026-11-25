package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "手机号或邮箱命中的已有人才")
public class HrTalentMatchVO {

    @Schema(description = "人才ID")
    private Long candidateId;

    @Schema(description = "姓名")
    private String displayName;

    @Schema(description = "人选编号")
    private String candidateNo;

    @Schema(description = "电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "目前公司")
    private String currentCompany;

    @Schema(description = "目前职位")
    private String currentTitle;

    @Schema(description = "在库状态码")
    private String poolStatus;

    @Schema(description = "在库状态")
    private String poolStatusName;
}
