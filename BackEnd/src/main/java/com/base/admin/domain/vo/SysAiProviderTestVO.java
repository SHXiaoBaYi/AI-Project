package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@Schema(description = "AI 厂商连通性探测结果")
public class SysAiProviderTestVO {

    @Schema(description = "是否调用成功")
    private boolean success;

    @Schema(description = "厂商标识")
    private String provider;

    @Schema(description = "最终成功使用的模型；失败则为最后尝试的模型")
    private String model;

    @Schema(description = "HTTP 状态码，成功时多为 200")
    private Integer httpStatus;

    @Schema(description = "说明")
    private String message;

    @Schema(description = "探测过程中尝试过的模型及结果摘要")
    @Builder.Default
    private List<String> attempts = new ArrayList<>();
}
