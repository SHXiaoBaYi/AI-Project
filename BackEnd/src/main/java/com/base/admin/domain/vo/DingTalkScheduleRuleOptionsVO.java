package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "日程规则下拉选项")
public class DingTalkScheduleRuleOptionsVO {

    @Schema(description = "系统用户")
    private List<Option> users = new ArrayList<>();

    @Schema(description = "招聘部门")
    private List<Option> departments = new ArrayList<>();

    @Schema(description = "岗位名称")
    private List<String> jobs = new ArrayList<>();

    @Data
    @Schema(description = "选项")
    public static class Option {
        private Long value;
        private String label;
        @Schema(description = "登录名等附加检索字段")
        private String username;

        public Option() {
        }

        public Option(Long value, String label) {
            this.value = value;
            this.label = label;
        }

        public Option(Long value, String label, String username) {
            this.value = value;
            this.label = label;
            this.username = username;
        }
    }
}
