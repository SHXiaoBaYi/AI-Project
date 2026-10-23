package com.base.admin.config;

import com.base.admin.service.GeoReviewKbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动后补齐原子审稿知识并删除旧整篇，避免依赖用户点「同步种子」。
 * 排在 Geo/Hr 菜单迁移之后。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 55)
@RequiredArgsConstructor
public class GeoReviewKbSeedRunner implements ApplicationRunner {

    private final GeoReviewKbService kbService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = kbService.seedDefaults(false);
            log.info("审稿知识库启动同步完成，变更 {} 条", n);
        } catch (Exception e) {
            log.error("审稿知识库启动同步失败: {}", e.getMessage(), e);
        }
    }
}
