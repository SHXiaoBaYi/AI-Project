package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "校准问答")
public class TrainQaCalibrateVO {

    @Schema(description = "ID", example = "1")
    private Long id;

    @Schema(description = "文档ID", example = "1")
    private Long docId;

    @Schema(description = "文档标题")
    private String docTitle;

    @Schema(description = "版本ID", example = "4")
    private Long versionId;

    @Schema(description = "版本标签")
    private String versionLabel;

    @Schema(description = "是否绑定当前最新版", example = "true")
    private Boolean latestVersion;

    @Schema(description = "分类", example = "cashier")
    private String category;

    @Schema(description = "用户问法")
    private String userQuestion;

    @Schema(description = "同义问法（换行分隔）")
    private String aliases;

    @Schema(description = "话题标题")
    private String topicTitle;

    @Schema(description = "答法纯文本")
    private String answer;

    @Schema(description = "答法 HTML")
    private String answerHtml;

    @Schema(description = "配图")
    private List<String> images = new ArrayList<>();

    @Schema(description = "状态 APPROVED/REJECTED/PENDING/STALE", example = "APPROVED")
    private String status;

    @Schema(description = "命中次数", example = "3")
    private Integer hitCount;

    @Schema(description = "来源 ASSISTANT_MARK/ADMIN_EDIT/RE_RECOGNIZE", example = "ASSISTANT_MARK")
    private String source;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private String updateTime;
}
