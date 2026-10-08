package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "培训文档")
public class TrainDocVO {

    private Long id;
    private String title;
    private String category;
    private String description;
    private Long latestVersionId;
    private Integer latestVersionNo;
    private String latestVersionLabel;
    private String latestFileName;
    private Integer versionCount;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
