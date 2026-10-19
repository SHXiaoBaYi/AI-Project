package com.base.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 所有 SchemaMigrator / 初始化 / 默认值回填跑完后打点。
 * 生产发布脚本轮询 {@code .schema-migrate-ok} 与日志 {@code PROD_RELEASE_READY}。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProdReleaseReady implements ApplicationRunner {

    static final String LOG_MARK = "PROD_RELEASE_READY";
    static final String MARKER_FILE = ".schema-migrate-ok";

    @Override
    public void run(ApplicationArguments args) {
        Path marker = Path.of("").toAbsolutePath().resolve(MARKER_FILE);
        try {
            Files.writeString(marker, Instant.now().toString() + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("写入 {} 失败: {}", marker, e.getMessage());
        }
        log.info("{} 表结构/初始化数据/默认值回填已执行完毕", LOG_MARK);
    }
}
