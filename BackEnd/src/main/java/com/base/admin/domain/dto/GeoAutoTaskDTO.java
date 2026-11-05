package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;
import java.util.List;

@Data
@Schema(description = "GEO自动监测-任务新增/修改")
public class GeoAutoTaskDTO {

    @Schema(description = "任务ID（新增不传，修改必传）", example = "1", nullable = true)
    private Long id;

    @NotBlank(message = "任务名不能为空")
    @Schema(description = "任务名", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心问题每日监测")
    private String taskName;

    @NotBlank(message = "圈选方式不能为空")
    @Schema(description = "圈选方式：TOPIC=按话题 / QUESTION=按问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "TOPIC")
    private String scopeType;

    @NotEmpty(message = "请至少圈选一个话题或问题")
    @Schema(description = "圈选ID列表（话题ID或问题ID）", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2,3]")
    private List<Long> scopeIds;

    @NotEmpty(message = "请至少选择一个监测平台")
    @Schema(description = "监测平台名列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"豆包\",\"DS\"]")
    private List<String> platforms;

    @Schema(description = "每日/每周", example = "每日")
    private String freq;

    @Schema(description = "每日运行时刻", example = "08:30")
    @DateTimeFormat(pattern = "HH:mm:ss")
    private LocalTime runTime;

    @Schema(description = "每周模式：1=周一 … 7=周日", example = "1")
    private Integer weekDay;

    @Schema(description = "同日同题同平台有人工记录时是否覆盖 0=人工优先 1=覆盖", example = "0")
    private Integer overwriteManual;

    @Schema(description = "变化提醒开关（P2 生效）", example = "1")
    private Integer notifyEnabled;

    @Schema(description = "1=启用 0=停用", example = "1")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;
}
