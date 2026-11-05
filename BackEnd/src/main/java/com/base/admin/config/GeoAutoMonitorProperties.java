package com.base.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GEO 自动监测（问题监控）配置。
 * 品牌词/竞品词为全局默认值，问题级可覆盖（geo_monitor_question.brand_words）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "geo.auto-monitor")
public class GeoAutoMonitorProperties {

    /** 是否启用定时扫描（任务级仍可单独停用） */
    private boolean enabled = true;

    /** 定时扫描间隔（毫秒） */
    private long scanMs = 30_000;

    /** 裁判模型厂商（判定提及/排名/情感），与被测平台解耦 */
    private String judgeProvider = "deepseek";

    /** 每平台并发上限（防风控/限流） */
    private int perPlatformConcurrency = 2;

    /** 单项最大重试次数（指数退避） */
    private int maxRetries = 2;

    /** 全局默认品牌词，逗号分隔 */
    private String brandWords = "小巴依";

    /** 全局默认竞品词，逗号分隔 */
    private String competitorWords = "";

    /** 送裁判的回答原文最大字符数（超长截断） */
    private int answerMaxChars = 8000;
}
