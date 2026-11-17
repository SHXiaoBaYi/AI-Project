package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 服务器登录窗口：打开、点击、输入、按键。不记录输入内容。 */
@Data
@Schema(description = "服务器登录窗口操作")
public class GeoBrowserBoothInputDTO {

    @NotNull(message = "请指定平台账号")
    @Schema(description = "平台账号ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    private Long accountId;

    @Schema(description = "操作：click / type / key", example = "click")
    private String action;

    @Schema(description = "点击横坐标（相对浏览器视口）", example = "640")
    private Double x;

    @Schema(description = "点击纵坐标（相对浏览器视口）", example = "360")
    private Double y;

    @Schema(description = "输入文本，一次最多 32 个字符")
    private String text;

    @Schema(description = "按键：Enter / Backspace / Tab / Escape / ArrowLeft / ArrowRight", example = "Enter")
    private String key;
}
