package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "答疑助手元信息")
public class TrainAssistantMetaVO {

    private String docTitle;
    private String latestVersionLabel;
    private Integer latestVersionNo;
    private Boolean hasDocument;
    private Boolean hasOlderVersions;
    private String category;
}
