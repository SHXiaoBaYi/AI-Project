package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "任务类型新增/修改")
public class SysTaskTypeDTO {

    @Schema(description = "主键（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotBlank(message = "类型名称不能为空")
    @Schema(description = "类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "文章发布")
    private String typeName;

    @Schema(description = "排序（越小越靠前）", example = "1")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "关联业务类型（如 geo_content_placement）", example = "geo_content_placement")
    private String bizType;

    @Schema(description = "分配回写业务字段编码（publisher/writer）", example = "writer")
    private String assignField;

    @Schema(description = "分配完成后派发的下一任务类型", example = "文章撰写")
    private String spawnTaskType;

    @Schema(description = "完成时是否必须上传证明附件", example = "true")
    private Boolean requireProof;
}
