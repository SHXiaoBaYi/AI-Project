package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "数据权限配置页下拉选项")
public class UserDataScopeMetaVO {

    @Schema(description = "话题选项")
    private List<Option> topics = new ArrayList<>();

    @Schema(description = "平台选项")
    private List<Option> platforms = new ArrayList<>();

    @Schema(description = "部门选项（扁平，带层级名）")
    private List<Option> departments = new ArrayList<>();

    @Schema(description = "任务类型选项")
    private List<Option> taskTypes = new ArrayList<>();

    @Data
    @Schema(description = "下拉项")
    public static class Option {
        @Schema(description = "值")
        private Long value;
        @Schema(description = "展示名")
        private String label;
        @Schema(description = "附加说明，如平台类型")
        private String extra;
    }
}
