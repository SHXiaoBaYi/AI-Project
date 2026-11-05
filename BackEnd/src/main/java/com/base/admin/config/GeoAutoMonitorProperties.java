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

    /** 每平台并发上限（防风控/限流）；v28 分批串行模式下保留作上限保护 */
    private int perPlatformConcurrency = 2;

    /** 单项最大重试次数（指数退避） */
    private int maxRetries = 2;

    /** 分批串行：每批监测多少个问题（默认 3，一批跑完才起下一批） */
    private int batchSize = 3;

    /** 批次间冷却（毫秒），给 ECS 留出释放浏览器/GC 的窗口 */
    private long batchIntervalMs = 5_000;

    /** 批次内相邻监测项间隔（毫秒），避免连续请求触发平台风控 */
    private long batchItemIntervalMs = 800;

    /** 单批次耗时预警阈值（毫秒）：超过则记录告警并把下一批冷却翻倍，不中断当前批避免漏数 */
    private long batchMaxDurationMs = 30 * 60_000L;

    /** 全局默认品牌词，逗号分隔 */
    private String brandWords = "小巴依";

    /** 全局默认竞品词，逗号分隔 */
    private String competitorWords = "";

    /** 送裁判的回答原文最大字符数（超长截断） */
    private int answerMaxChars = 8000;
}
