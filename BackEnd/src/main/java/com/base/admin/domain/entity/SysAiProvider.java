package com.base.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.base.admin.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_provider")
@Schema(description = "AI 厂商配置")
public class SysAiProvider extends BaseEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "厂商标识：tongyi/deepseek/zhipu/moonshot", example = "tongyi")
    private String provider;

    @Schema(description = "展示名", example = "通义千问")
    private String providerName;

    @Schema(description = "API Key（库内明文，接口返回脱敏）")
    private String apiKey;

    @Schema(description = "模型名", example = "qwen-turbo")
    private String model;

    @Schema(description = "兼容接口 baseUrl，空则用内置预设")
    private String baseUrl;

    @Schema(description = "是否启用 1=启用 0=停用", example = "1")
    private Integer enabled;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;
}
