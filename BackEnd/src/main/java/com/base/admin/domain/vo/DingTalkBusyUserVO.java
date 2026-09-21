package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "一个用户在查询范围内的闲忙")
public class DingTalkBusyUserVO {

    @Schema(description = "系统用户ID")
    private Long userId;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "姓名")
    private String nickname;

    @Schema(description = "钉钉返回的错误，为空表示查询成功")
    private String error;

    @Schema(description = "按开始时间排序的闲忙时间段")
    private List<DingTalkBusySlotVO> slots = new ArrayList<>();
}
