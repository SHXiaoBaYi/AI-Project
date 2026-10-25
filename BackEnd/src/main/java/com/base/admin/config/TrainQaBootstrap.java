package com.base.admin.config;

import com.base.admin.service.TrainQaItemService;
import com.base.admin.service.TrainQaVectorIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动后先装载已有题目向量，再为尚未拆题的历史文档补拆。不删原文件。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 80)
@RequiredArgsConstructor
public class TrainQaBootstrap implements ApplicationRunner {

    private final TrainQaVectorIndex vectorIndex;
    private final TrainQaItemService trainQaItemService;

    @Override
    public void run(ApplicationArguments args) {
        vectorIndex.reload();
        Thread worker = new Thread(() -> {
            try {
                trainQaItemService.backfillMissingLatest();
            } catch (Exception e) {
                log.warn("历史培训文档拆题未完成，原文件仍可用于答疑: {}", e.getMessage());
            }
        }, "train-qa-backfill");
        worker.setDaemon(true);
        worker.start();
    }
}
