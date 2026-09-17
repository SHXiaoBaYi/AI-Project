package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "任务完成（去完成）")
public class SysTaskCompleteDTO {

    @Schema(description = "完成说明")
    private String remark;

    @Schema(description = "完成证明附件（已上传的文件信息）")
    private List<SysTaskFileItemDTO> files = new ArrayList<>();
}
