package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "任务列表/详情")
public class SysTaskVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "任务说明")
    private String content;

    @Schema(description = "任务类型")
    private String taskType;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "是否逾期")
    private boolean overdue;

    @Schema(description = "进度")
    private Integer progress;

    @Schema(description = "创建人用户ID")
    private Long creatorUserId;

    @Schema(description = "创建人")
    private String creatorName;

    @Schema(description = "负责人用户ID")
    private Long ownerUserId;

    @Schema(description = "负责人")
    private String ownerName;

    @Schema(description = "执行人用户ID列表")
    private List<Long> assigneeUserIds = new ArrayList<>();

    @Schema(description = "执行人姓名（逗号拼接）")
    private String assigneeNames;

    @Schema(description = "执行人明细")
    private List<SysTaskAssigneeVO> assignees = new ArrayList<>();

    @Schema(description = "计划开始")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planStartTime;

    @Schema(description = "计划截止")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planEndTime;

    @Schema(description = "实际开始")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际完成")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    @Schema(description = "关联业务类型")
    private String bizType;

    @Schema(description = "关联业务ID")
    private Long bizId;

    @Schema(description = "关联业务摘要")
    private String bizTitle;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否允许删除（外源生成的业务任务不可删）", example = "true")
    private boolean deletable = true;

    @Schema(description = "是否可分配", example = "true")
    private boolean assignable = false;

    @Schema(description = "是否可去完成（未开始/进行中）", example = "true")
    private boolean completable = false;

    @Schema(description = "完成时是否必须上传证明", example = "true")
    private boolean requireProof = false;

    @Schema(description = "附件数量")
    private Integer fileCount = 0;

    @Schema(description = "任务附件（详情时返回）")
    private List<SysTaskFileVO> files = new ArrayList<>();

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
