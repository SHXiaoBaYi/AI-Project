package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "培训文档保存")
public class TrainDocSaveDTO {

    @Schema(description = "文档ID，空则新增")
    private Long id;

    @NotBlank
    @Schema(description = "标题")
    private String title;

    @Schema(description = "分类，默认 cashier")
    private String category;

    @Schema(description = "说明")
    private String description;
}
