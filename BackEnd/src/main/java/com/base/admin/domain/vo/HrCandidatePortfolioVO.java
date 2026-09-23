package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "候选人作品集附件")
public class HrCandidatePortfolioVO {

    @Schema(description = "附件ID")
    private Long id;

    @Schema(description = "候选人ID")
    private Long candidateId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "扩展名")
    private String fileExt;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "创建时间")
    private String createTime;
}
