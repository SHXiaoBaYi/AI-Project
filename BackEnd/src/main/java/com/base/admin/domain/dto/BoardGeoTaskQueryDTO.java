package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "GEO任务看板查询（筛选作用于全部维度）")
public class BoardGeoTaskQueryDTO {

    @Schema(description = "开始日期 yyyy-MM-dd")
    private String startDate;

    @Schema(description = "结束日期 yyyy-MM-dd")
    private String endDate;

    @Schema(description = "粒度 day/week/month/year")
    private String grain;

    @Schema(description = "撰写人用户ID多选；空表示不限")
    private List<Long> filterUserIds;

    @Schema(description = "话题ID多选；空表示不限")
    private List<Long> topicIds;

    @Schema(description = "任务类型多选；空表示不限（仍限定GEO相关任务）")
    private List<String> taskTypes;
}
