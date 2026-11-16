package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "内容投放主表新增/修改")
public class GeoContentPlacementDTO {

    @Schema(description = "主键（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @Schema(description = "发布人用户ID（空表示待分配）", example = "1", nullable = true)
    private Long publisherUserId;

    @Schema(description = "撰写人用户ID（空表示待分配）", example = "2", nullable = true)
    private Long ownerUserId;

    @Schema(description = "话题ID（优先；与话题名称二选一）", example = "1", nullable = true)
    private Long topicId;

    @Schema(description = "话题名称（无 ID 时按名称 getOrCreate）", example = "新疆羊肉")
    private String topicName;

    @NotBlank(message = "目标问题不能为空")
    @Schema(description = "目标问题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetQuestion;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否有视频任务。勾选后只生成抖音视频剪辑/发布，不生成文章撰写/发布；取消勾选则反过来。编辑来回切换时，未完成的对应任务取消或补上", example = "false", nullable = true)
    private Boolean hasVideoTask;
}
