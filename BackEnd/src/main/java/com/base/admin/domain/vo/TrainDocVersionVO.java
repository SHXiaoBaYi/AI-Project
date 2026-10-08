package com.base.admin.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "培训文档版本")
public class TrainDocVersionVO {

    private Long id;
    private Long docId;
    private Integer versionNo;
    private String versionLabel;
    private String fileName;
    private Long fileSize;
    private String remark;
    private Boolean latest;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String createBy;
}
