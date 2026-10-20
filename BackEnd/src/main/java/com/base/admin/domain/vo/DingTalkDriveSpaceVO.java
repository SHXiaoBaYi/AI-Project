package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "企业钉盘空间")
public class DingTalkDriveSpaceVO {

    @Schema(description = "空间 ID")
    private String spaceId;

    @Schema(description = "空间名称")
    private String spaceName;

    @Schema(description = "是否推荐用于简历（名称含简历/招聘/hr）")
    private Boolean preferred;
}
