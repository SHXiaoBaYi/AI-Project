package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "任务附件项")
public class SysTaskFileItemDTO {

    @NotBlank(message = "文件名不能为空")
    @Schema(description = "原始文件名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileName;

    @NotBlank(message = "文件地址不能为空")
    @Schema(description = "访问路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "/uploads/task/xxx.pdf")
    private String fileUrl;

    @Schema(description = "字节大小")
    private Long fileSize;

    @Schema(description = "MIME")
    private String contentType;
}
