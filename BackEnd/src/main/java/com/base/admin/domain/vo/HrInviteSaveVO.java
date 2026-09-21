package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "面试邀约保存结果")
public class HrInviteSaveVO {

    @Schema(description = "邀约ID")
    private Long id;

    @Schema(description = "未创建或未取消钉钉日程时的提示，为空表示日程已处理")
    private String warning;

    @Schema(description = "本次成功创建的钉钉日程条数")
    private Integer created;

    @Schema(description = "没有绑定钉钉、因此没能创建日程的面试官")
    private List<String> unboundInterviewers;
}
