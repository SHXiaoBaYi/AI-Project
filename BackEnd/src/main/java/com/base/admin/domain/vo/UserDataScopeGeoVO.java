package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "GEO 模块数据权限切片")
public class UserDataScopeGeoVO {

    @Schema(description = "是否启用 GEO 切片（关闭=该模块不额外收窄）", example = "true")
    private Boolean enabled = false;

    @Schema(description = "可见话题 ID（空=不按话题限制）")
    private List<Long> topicIds = new ArrayList<>();

    @Schema(description = "可见平台 ID（空=不按平台限制；与话题同时配置时为且）")
    private List<Long> platformIds = new ArrayList<>();

    @Schema(description = "仅本人负责的日监测", example = "false")
    private Boolean selfOwnerOnly = false;

    @Schema(description = "仅本人撰写的投放", example = "false")
    private Boolean selfWriterOnly = false;

    @Schema(description = "仅本人发布的投放", example = "false")
    private Boolean selfPublisherOnly = false;
}
