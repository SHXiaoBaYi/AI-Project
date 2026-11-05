package com.base.admin.domain.dto;

import com.base.admin.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "GEO自动监测-结果分页查询")
public class GeoAutoResultQueryDTO extends PageQuery {

    @Schema(description = "监测日期起（含）", example = "2026-11-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateStart;

    @Schema(description = "监测日期止（含）", example = "2026-11-07")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateEnd;

    @Schema(description = "平台名", example = "豆包")
    private String platform;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "问题文本模糊搜索")
    private String questionText;

    @Schema(description = "是否提及 1=是 0=否", example = "1")
    private Integer mentioned;

    @Schema(description = "情感：正面/中性/负面")
    private String sentiment;

    @Schema(description = "结果状态：SUCCESS/SKIPPED_MANUAL/JUDGE_FAILED/CALL_FAILED")
    private String status;

    @Schema(description = "批次ID", example = "1")
    private Long runId;
}
