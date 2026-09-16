package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_monitor_daily")
@Schema(description = "GEO日监测")
public class GeoMonitorDaily extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "巡查日期", example = "2026-09-01")
    private LocalDate inspectDate;

    @Schema(description = "长短词（日巡查/周巡查）", example = "日巡查")
    private String termType;

    @Schema(description = "平台", example = "豆包")
    private String platform;

    @Schema(description = "关键字/提问问题", example = "新疆适合寄内地的礼品")
    private String keyword;

    @Schema(description = "负责人用户ID", example = "1", nullable = true)
    private Long ownerUserId;

    @Schema(description = "负责人展示名（昵称优先，否则用户名）", example = "张三")
    private String ownerName;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "是否露出（1=是 0=否）", example = "1")
    private Integer mentioned;

    @Schema(description = "排名", example = "1", nullable = true)
    private Integer rankNo;

    @Schema(description = "推荐状态", example = "出现且推荐")
    private String recommendStatus;

    @Schema(description = "截图URL")
    private String screenshotUrl;

    @Schema(description = "第三方链接")
    private String thirdPartyUrl;

    @Schema(description = "负面/错误内容")
    private String negativeContent;

    @Schema(description = "出现的竞品")
    private String competitors;

    @Schema(description = "是否已被周/月/年统计（1=已统计不可改 0=未统计可编辑）", example = "0")
    private Integer boardLocked;
}
